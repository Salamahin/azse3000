//package com.aswatson.aswrdm.azse3000.program
//
//import java.io.IOException
//import java.net.URI
//
//import com.aswatson.aswrdm.azse3000.Main.ENV
//import com.aswatson.aswrdm.azse3000.azure.AzureEndpoints
//import com.aswatson.aswrdm.azse3000.expression.InputParser
//import com.aswatson.aswrdm.azse3000.preprocess.{ConfigurationBasedPathRefinery, ExpandCurlyBracesRefinery}
//import com.aswatson.aswrdm.azse3000.shared._
//import com.microsoft.azure.storage.blob.{CloudBlobContainer, CloudBlockBlob}
//import zio.console.{Console, getStrLn, putStr, putStrLn}
//import zio._
//
//trait Configurable {
//  def conf: Configurable.Service
//}
//
//object Configurable {
//  trait Service {
//    def parallelism: Int
//    def knownHosts: Map[String, String]
//    def knownSecrets: Map[String, Map[String, String]]
//  }
//}
//
//
//class Program {
//
//
//  class ConsolePrompt(knownSecrets: Map[String, Map[String, String]]) extends Prompt[RIO[Console, *]] {
//    override def command =
//      for {
//        _     <- putStrLn("Enter command:")
//        input <- getStrLn
//      } yield Command(input)
//  }
//
//  class ConsoleProgressWatcher {
//    def lookAfter[T](program: RIO[ENV, T]): RIO[ENV, T] = {
//      import zio.duration._
//
//      val showProgress = (for {
//        _ <- putStr(".")
//        _ <- ZIO.sleep(1 second)
//        _ <- putStr(".")
//        _ <- ZIO.sleep(1 second)
//        _ <- putStr(".\r")
//        _ <- ZIO.sleep(1 second)
//      } yield ()).repeat(Schedule.forever)
//
//      for {
//        progress <- showProgress.fork
//        result   <- program
//        _        <- progress.interrupt
//        _        <- putStrLn("")
//      } yield result
//    }
//  }
//
//  class ZioParallel(parallelism: Int) extends Parallel[Task] {
//    override def traverse[T, U](items: Seq[T])(action: T => Task[U]) =
//      ZIO.traversePar(items)(action)
//
//    override def traverseN[T, U](items: Seq[T])(action: T => Task[U]) =
//      ZIO.traverseParN(parallelism)(items)(action)
//  }
//
//  class Desugarer(knownHosts: Map[String, String]) extends CommandSyntax[UIO] {
//    override def desugar(command: Command): UIO[Command] = UIO {
//      val expanded = ExpandCurlyBracesRefinery.expand(command)
//      ConfigurationBasedPathRefinery.refinePaths(knownHosts)(expanded)
//    }
//  }
//
//  class ConfigurableVaultWithConsolePrompt(knownSecrets: Map[String, Map[String, String]]) extends Vault[RIO[Console, *]] {
//    override def credsFor(acc: Account, cont: Container) = {
//      val credsFromConf = UIO {
//        for {
//          accSecrets <- knownSecrets.get(acc.name)
//          secret     <- accSecrets.get(cont.name)
//        } yield Secret(secret)
//      }
//
//      val promptCreds = for {
//        _     <- putStrLn(s"Enter SAS for ${cont.name}@${acc.name}:")
//        input <- getStrLn
//      } yield input
//
//       credsFromConf.orElse(promptCreds)
//    }
//  }
//
////  class ZioParse[E] extends Parse[RIO[E, *]] {
////    override def toExpression(prompted: Command) = URIO(InputParser(prompted))
////  }
////
////  class ZioAzure[E] extends Endpoint[RIO[E, *], CloudBlockBlob, CloudBlobContainer] {
////
////    override def pathWithinContainer(file: CloudBlockBlob): RIO[E, Path] = Task {
////      file.getUri match {
////        case AzureEndpoints(_, _, prefix) => prefix
////      }
////    }
////
////    override def decompose(path: Path): RIO[E, Either[MalformedPath, (Account, Container, Path)]] = URIO {
////      import cats.syntax.either._
////
////      try {
////        val uri = URI.create(path.path)
////        uri match {
////          case AzureEndpoints(acc, cont, prefix) => (acc, cont, prefix).asRight
////        }
////      } catch {
////        case _: Throwable => MalformedPath(path).asLeft
////      }
////    }
////
////    override def toBlob(secret: Secret, fullPath: Path): RIO[E, CloudBlockBlob] = Task {
////      AzureEndpoints.toFile(fullPath, secret)
////    }
////
////    override def toContainer(secret: Secret, excessPath: Path): RIO[E, Either[NoSuchContainer, CloudBlobContainer]] =
////      URIO {
////        import cats.syntax.either._
////
////        try {
////          val uri = URI.create(excessPath.path)
////          uri match {
////            case AzureEndpoints(acc, cont, _) => AzureEndpoints.toContainer(acc, cont, secret).asRight
////          }
////        } catch {
////          case _: Throwable => NoSuchContainer(excessPath).asLeft
////        }
////      }
////  }
//
//  def run() = {}
//}
