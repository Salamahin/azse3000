package com.aswatson.aswrdm.azse3000

import java.net.URI

import com.aswatson.aswrdm.azse3000.Main.ENV
import com.aswatson.aswrdm.azse3000.azure.{AzureEndpoints, ZioAzureFileSystem}
import com.aswatson.aswrdm.azse3000.configurable.{Config, ConfigurationBasedPathRefinery}
import com.aswatson.aswrdm.azse3000.expression.InputParser
import com.aswatson.aswrdm.azse3000.shared._
import com.microsoft.azure.storage.blob.{CloudBlobContainer, CloudBlockBlob}
import zio._
import zio.clock.Clock
import zio.console.{Console, getStrLn, putStr, putStrLn}

class ConsoleZioPrompt(knownSecrets: Map[String, Map[String, String]]) extends Prompt[RIO[ENV, *]] {
  override def command =
    for {
      _     <- putStrLn("Enter command:")
      input <- getStrLn
    } yield Command(input)

  override def creds(acc: Account, cont: ContainerName) =
    for {
      -     <- putStrLn(s"Enter SAS token for ${cont.name}@${acc.name}:")
      token <- getStrLn
    } yield Secret(token)
}

class ConsoleProgressWatcher extends Watcher[RIO[ENV, *]] {
  override def lookAfter[T](program: RIO[ENV, T]): RIO[ENV, T] = {
    import zio.duration._

    val showProgress = (for {
      _ <- putStr(".")
      _ <- ZIO.sleep(1 second)
      _ <- putStr(".")
      _ <- ZIO.sleep(1 second)
      _ <- putStr(".\r")
      _ <- ZIO.sleep(1 second)
    } yield ()).repeat(Schedule.forever)

    for {
      _        <- putStrLn("")
      progress <- showProgress.fork
      result   <- program
      _        <- progress.interrupt
      _        <- putStrLn("")
    } yield result
  }
}

class ZioParallel[E](parallelism: Int) extends Parallel[RIO[E, *]] {
  override def traverse[T, U](items: Seq[T])(action: T => RIO[E, U]): RIO[E, Seq[U]] =
    ZIO.traversePar(items)(action)

  override def traverseN[T, U](items: Seq[T])(action: T => RIO[E, U]): RIO[E, Seq[U]] =
    ZIO.traverseParN(parallelism)(items)(action)
}

class ZioPathRefinery[E](knownHosts: Map[String, String]) extends Refine[RIO[E, *]] {
  override def path(path: Path): RIO[E, Path] = UIO {
    ConfigurationBasedPathRefinery.refine(knownHosts)(path)
  }
}

class ZioSecretsRepo[E](knownSecrets: Map[String, Map[String, String]]) extends CredsRepo[RIO[E, *]] {
  override def creds(acc: Account, cont: ContainerName): RIO[E, Option[Secret]] = UIO {
    for {
      accSecrets <- knownSecrets.get(acc.name)
      secret     <- accSecrets.get(cont.name)
    } yield Secret(secret)
  }
}

class ZioParse[E] extends Parse[RIO[E, *]] {
  override def toExpression(prompted: Command) = URIO(InputParser(prompted))
}

class ZioAzureUri[E] extends EndpointUri[RIO[E, *], CloudBlockBlob, CloudBlobContainer] {

  override def pathWithinContainer(file: CloudBlockBlob): RIO[E, Path] = Task {
    file.getUri match {
      case AzureEndpoints(_, _, prefix) => prefix
    }
  }
  override def decompose(path: Path): RIO[E, Either[MalformedPath, (Account, ContainerName, Path)]] = URIO {
    import cats.syntax.either._

    try {
      val uri = URI.create(path.path)
      uri match {
        case AzureEndpoints(acc, cont, prefix) => (acc, cont, prefix).asRight
      }
    } catch {
      case _: Throwable => MalformedPath(path).asLeft
    }
  }

  override def toFile(secret: Secret, fullPath: Path): RIO[E, CloudBlockBlob] = Task {
    AzureEndpoints.toFile(fullPath, secret)
  }

  override def findContainer(secret: Secret, excessPath: Path): RIO[E, Either[NoSuchContainer, CloudBlobContainer]] =
    URIO {
      import cats.syntax.either._

      try {
        val uri = URI.create(excessPath.path)
        uri match {
          case AzureEndpoints(acc, cont, _) => AzureEndpoints.toContainer(acc, cont, secret).asRight
        }
      } catch {
        case _: Throwable => NoSuchContainer(excessPath).asLeft
      }
    }
}

object Main extends zio.App {
  type ENV = Clock with Console

  private def loadConf() = {
    import pureconfig._
    import pureconfig.generic.auto._

    ConfigSource
      .file("secrets.conf")
      .optional
      .withFallback(ConfigSource.default)
      .loadOrThrow[Config]
  }

  private def formatOperationReport(description: OperationDescription, stats: OperationResult) = {
    def formatFailures(failures: Seq[FileOperationFailed]) = {
      if (failures.isEmpty) "none"
      else failures.map(f => s"  * ${f.file.path}: ${f.th.getCause}").mkString("\n")
    }

    s"""${description.description}
       |Successfully processed ${stats.succeed} items
       |Failures: ${formatFailures(stats.errors)}
       |""".stripMargin
  }

  private def formatIssues(issues: Seq[Issue with Aggregate]) = {
    issues
      .map {
        case InvalidCommand(msg)   => s"  * Failed to parse an expression: $msg"
        case MalformedPath(path)   => s"  * Format of path ${path.path} is unexpected"
        case NoSuchContainer(path) => s"  * Failed to find a container of path ${path.path}"
      }
      .mkString("\n")
  }

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = {
    val conf = loadConf()

    import zio.interop.catz.core._

    implicit val wd: Watcher[RIO[ENV, *]]                                               = new ConsoleProgressWatcher
    implicit val par: Parallel[RIO[ENV, *]]                                              = new ZioParallel(conf.parallelism)
    implicit val prompt: Prompt[RIO[ENV, *]]                                             = new ConsoleZioPrompt(conf.knownSecrets)
    implicit val parse: Parse[RIO[ENV, *]]                                               = new ZioParse
    implicit val credsVault: CredsRepo[RIO[ENV, *]]                                      = new ZioSecretsRepo(conf.knownSecrets)
    implicit val pathRefine: Refine[RIO[ENV, *]]                                         = new ZioPathRefinery(conf.knownHosts)
    implicit val endpoints: EndpointUri[RIO[ENV, *], CloudBlockBlob, CloudBlobContainer] = new ZioAzureUri
    implicit val fs: FileSystem[RIO[ENV, *], CloudBlockBlob, CloudBlobContainer] = new ZioAzureFileSystem(
      conf.parallelism
    )

    def attempt: ZIO[ENV, Throwable, Unit] = {
      (for {
        results <- new Program[RIO[ENV, *], CloudBlockBlob, CloudBlobContainer].run.absolve

        report = results
          .map {
            case (descr, stats) => formatOperationReport(descr, stats)
          }
          .mkString("\n")

        _ <- putStrLn(s"Execution summary:\n$report")
      } yield ()).catchSome {
        case Failure(issues) => putStrLn(s"Failed to interpret:\n${formatIssues(issues)}\n") *> attempt
      }
    }

    attempt
      .catchAllCause(
        failure => ZIO.effect(System.err.println(failure.prettyPrint)) *> ZIO.halt(failure)
      )
      .fold(_ => 1, _ => 0)
  }
}
