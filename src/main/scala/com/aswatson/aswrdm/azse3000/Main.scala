package com.aswatson.aswrdm.azse3000

import java.net.URI

import com.aswatson.aswrdm.azse3000.azure.{AzureEndpoints, ZioAzureFileSystem}
import com.aswatson.aswrdm.azse3000.configurable.{Config, ConfigurationBasedPathRefinery}
import com.aswatson.aswrdm.azse3000.expression.InputParser
import com.aswatson.aswrdm.azse3000.shared._
import com.microsoft.azure.storage.blob.{CloudBlobContainer, CloudBlockBlob}
import zio._
import zio.console.{Console, getStrLn, putStrLn}

class ConsoleZioPrompt(knownSecrets: Map[String, Map[String, String]]) extends Prompt[RIO[Console, *]] {
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

class ZioPathRefinery[E](knownHosts: Map[String, String]) extends Refine[RIO[E, *]] {
  override def path(path: Path): RIO[E, Path] = UIO {
    ConfigurationBasedPathRefinery.refine(knownHosts)(path)
  }
}

class ZioEncryptedSecretsRepo[E](knownSecrets: Map[String, Map[String, String]]) extends CredsRepo[RIO[E, *]] {
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

  def loadConf() = {
    import pureconfig._
    import pureconfig.generic.auto._

    ConfigSource
      .file("secrets.conf")
      .optional
      .withFallback(ConfigSource.default)
      .loadOrThrow[Config]
  }

  def formatOperationReport(description: OperationDescription, stats: OperationResult) = {
    def formatFailures(failures: Seq[FileOperationFailed]) = {
      if (failures.isEmpty) "none"
      else failures.map(f => s"  * ${f.file.path}: ${f.th.getCause}").mkString("\n")
    }

    s"""${description.description}
       |Successfully processed ${stats.succeed}
       |Failures: ${formatFailures(stats.errors)}
       |""".stripMargin
  }

  def formatIssues(issues: Seq[Issue with Aggregate]) = {
    issues
      .map {
        case InvalidCommand(msg)   => s"  * Failed to parse an expression: $msg"
        case MalformedPath(path)   => s"  * Format of $path is unexpected"
        case NoSuchContainer(path) => s"  * Failed to find a container of $path"
      }
      .mkString("\n")
  }

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = {
    implicit val conf = loadConf()

    import nequi.zio.logger._
    import zio.interop.catz.core._

    implicit val prompt: Prompt[RIO[Console, *]]                                             = new ConsoleZioPrompt(conf.knownSecrets)
    implicit val parse: Parse[RIO[Console, *]]                                               = new ZioParse
    implicit val credsVault: CredsRepo[RIO[Console, *]]                                      = new ZioEncryptedSecretsRepo(conf.knownSecrets)
    implicit val pathRefine: Refine[RIO[Console, *]]                                         = new ZioPathRefinery(conf.knownHosts)
    implicit val endpoints: EndpointUri[RIO[Console, *], CloudBlockBlob, CloudBlobContainer] = new ZioAzureUri
    implicit val fs: FileSystem[RIO[Console, *], CloudBlockBlob, CloudBlobContainer] = new ZioAzureFileSystem(
      conf.batchSize
    )

    def attempt: ZIO[Logger with Console, Throwable, Unit] = {
      (for {
        results <- new Program[RIO[Console, *], CloudBlockBlob, CloudBlobContainer].run.absolve

        report = results
          .map {
            case (descr, stats) => formatOperationReport(descr, stats)
          }
          .mkString("\n")

        _ <- info(s"Execution summary:\n$report")
      } yield ()).catchSome {
        case Failure(issues) => error(s"Failed to interpret:\n${formatIssues(issues)}") *> attempt
      }
    }

    attempt
      .catchAllCause(
        failure => ZIO.effect(System.err.println(failure.prettyPrint)) *> ZIO.halt(failure)
      )
      .provideSome[Console](
        env =>
          new Logger with Console {
            override val logger: Logger.Service[Any]   = Slf4jLogger.create.logger
            override val console: Console.Service[Any] = env.console
          }
      )
      .fold(_ => 1, _ => 0)
  }
}
