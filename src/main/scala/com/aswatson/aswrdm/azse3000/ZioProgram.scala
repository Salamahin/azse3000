package com.aswatson.aswrdm.azse3000

import java.time.Instant

import com.aswatson.aswrdm.azse3000.azure.{AzureEndpoints, AzureFlatListingFileSystem, AzureRecursiveListingFileSystem}
import com.aswatson.aswrdm.azse3000.configurable.{Config, FlatLimited, Recursive}
import com.aswatson.aswrdm.azse3000.expression.CommandParser
import com.aswatson.aswrdm.azse3000.program.{FileSystemEngine, UserInterface}
import com.aswatson.aswrdm.azse3000.shared._
import com.aswatson.aswrdm.azse3000.shared.types.CREDS
import com.aswatson.aswrdm.azse3000.syntax.ShellLikeSyntax
import zio._

class ZioProgram {
  import cats.syntax.either._
  import zio.console._
  import zio.duration._
  import zio.interop.catz.core._

  private def prompt = new Prompt[RIO[Console, *]] {
    override def command: RIO[Console, Command] =
      for {
        _     <- putStrLn("Enter command:")
        input <- getStrLn
      } yield Command(input)
  }

  private def syntax(conf: Config) = new ShellLikeSyntax[RIO[Console, *]](conf.knownHosts)

  private def parse = new Parse[RIO[Console, *]] {
    override def toExpression(prompted: Command): RIO[Console, Either[InvalidCommand, Expression[Path]]] = RIO {
      CommandParser(prompted)
    }

    override def toFullPath(inputPath: Path): RIO[Console, Either[MalformedPath, ParsedPath]] = UIO {
      inputPath match {
        case AzureEndpoints(pp) => pp.asRight
        case _                  => MalformedPath(s"Unexpected path format: ${inputPath.path}").asLeft
      }
    }
  }

  private def vault(conf: Config) = new Vault[RIO[Console, *]] {
    override def credsFor(acc: Account, cont: Container): RIO[Console, Secret] =
      ZIO
        .fromOption(
          conf
            .knownSecrets
            .get(acc.name)
            .flatMap(_.get(cont.name))
        )
        .orElse {
          for {
            _           <- putStrLn(s"Enter SAS for ${cont.name}@${acc.name}")
            inputSecret <- getStrLn
          } yield inputSecret
        }
        .map(Secret)
  }

  private def endpoint(creds: CREDS) = new AzureEndpoints[UIO](creds)

  private def parallel(conf: Config) = new Parallel[UIO] {
    override def traverse[T, U](items: Seq[T])(action: T => UIO[U]): UIO[Seq[U]] =
      UIO.traverseParN(conf.parallelism)(items)(action)

    override def zip[T, U](first: UIO[T], second: UIO[U]): UIO[(T, U)] = first <&> second
  }

  private def fs(conf: Config) = {
    conf.listingMode match {
      case FlatLimited(fetchMax) => new AzureFlatListingFileSystem[UIO](fetchMax, parallel(conf))
      case Recursive             => new AzureRecursiveListingFileSystem[UIO](parallel(conf))
    }
  }

  private def conf: Config = {
    import pureconfig._
    import pureconfig.generic.auto._

    ConfigSource
      .file("secrets.conf")
      .optional
      .withFallback(ConfigSource.default)
      .loadOrThrow[Config]
  }

  private def ui(conf: Config) = new UserInterface[RIO[Console, *]](prompt, syntax(conf), parse, vault(conf))

  private def engine(creds: CREDS, conf: Config) =
    new FileSystemEngine(endpoint(creds), parallel(conf), fs(conf))

  private def progressBar[R, T, E](program: ZIO[R, T, E]) = {
    val showProgress = (for {
      _ <- putStr(".")
      _ <- ZIO.sleep(1 second)
      _ <- putStr(".")
      _ <- ZIO.sleep(1 second)
      _ <- putStr(".\r")
      _ <- ZIO.sleep(1 second)
    } yield ()).repeat(Schedule.forever)

    for {
      progress <- showProgress.fork
      result   <- program
      _        <- progress.interrupt
      _        <- putStrLn("")
    } yield result
  }

  private def measure[R, T, E](program: ZIO[R, T, E]) =
    for {
      start    <- UIO.succeed(Instant.now)
      result   <- program
      duration = java.time.Duration.between(start, Instant.now)
    } yield (result, duration)

  private def formatOperationReport(description: OperationDescription, stats: EvaluationSummary) = {
    def formatFailures(failures: Seq[ActionFailed]) = {
      if (failures.isEmpty) "none"
      else "\n" + failures.map(f => s"    * ${f.msg}: ${f.th.getCause}").mkString("\n")
    }

    s"""  * ${description.description}
       |    Successfully processed ${stats.succeed} items
       |    Failures: ${formatFailures(stats.errors)}""".stripMargin
  }

  private def formatIssues(issues: Seq[Fatal with Aggregate]) =
    issues
      .map {
        case InvalidCommand(msg) => s"  * Failed to parse an expression: $msg"
        case MalformedPath(msg)  => s"  * Format of path $msg is unexpected"
        case FileSystemFailure(msg, throwable) =>
          s"""  * Operations under path $msg failed because "${throwable.getMessage}""""
      }
      .mkString("\n")

  private def formatResults(results: Map[OperationDescription, EvaluationSummary]) = {
    val report = results
      .map {
        case (descr, stats) => formatOperationReport(descr, stats)
      }
      .mkString("\n")

    s"Execution summary:\n$report"
  }

  def run() = {
    val c = conf

    val attempt = ui(c)
      .run()
      .absolve
      .flatMap {
        case (expression, creds) =>
          progressBar {
            measure {
              engine(creds, c).evaluate(expression).absolve
            }
          }
      }
      .flatMap {
        case (results, duration) => putStrLn(formatResults(results)) *> putStr(s"\nDuration: $duration")
      }

    attempt
      .catchSome {
        case AggregatedFatal(issues) => putStrLn(s"Failed to interpret:\n${formatIssues(issues)}\n") *> attempt
      }
      .catchAllCause(
        failure => ZIO.effect(System.err.println(failure.prettyPrint)) *> ZIO.halt(failure)
      )
      .fold(_ => 1, _ => 0)
  }
}

object ZioProgram extends zio.App {
  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = {
    new ZioProgram().run()
  }
}
