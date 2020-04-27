package io.github.salamahin.azse3000.shared

import cats.Monad
import cats.data.{EitherT, OptionT}
import cats.free.Free._
import com.microsoft.azure.storage.blob.CloudBlockBlob
import io.github.salamahin.azse3000.expression.ActionInterpret2
import io.github.salamahin.azse3000.shared.types.CREDS
import zio.Fiber.Id

object UiProgram {
  import ExecStrategy._
  import cats.implicits._

  def program[F[_]: Monad](
    implicit ui: UserInterface[F],
    parser: Parser[F],
    configuration: Configuration[F],
    azure: AzureEngine[F],
    concurrentController: ConcurrentController[F]
  ) = {
    val collectPaths =
      ActionInterpret2.interpret2[Seq[ParsedPath]]({
        case Copy(from, to) => from :+ to
        case Move(from, to) => from :+ to
        case Remove(from)   => from
        case Count(in)      => in
      }) _

    def collectCreds(paths: Seq[ParsedPath]) =
      paths
        .groupBy(p => (p.account, p.container))
        .keys
        .toVector
        .traverse {
          case id @ (acc, cont) =>
            OptionT(configuration.readCreds(acc, cont).seq)
              .getOrElseF(ui.promptCreds(acc, cont).seq)
              .map(secret => id -> secret)
              .asProgramStep
        }
        .map(_.toMap)

    def copyBlob(from: ParsedPath, fromBlob: CloudBlockBlob, to: ParsedPath, secrets: CREDS) = {
      val startCopy = EitherT(azure.relativize(from, fromBlob, to, secrets(to.account, to.container)).seq)
        .map { toBlob =>
          toBlob.startCopy(fromBlob)
          toBlob
        }

//      def waitUntilCopied = concurrentController.delayCopyStatusCheck().seq *> blob

//      for {
//        toBlob <- azure
//                   .relativize(from, fromBlob, to, secrets(to.account, to.container))
//                   .seq
//                   .map { toBlob =>
//                     toBlob.startCopy(fromBlob)
//                     toBlob
//                   }
////          .map(identity)
////          .iterateUntil(_.getCopyState.getStatus == CopyStatus.PENDING)
//
//      } yield ()
    }


//    def relativizeAll(from: ParsedPath, blobs: Seq[CloudBlockBlob], to: ParsedPath, secrets: CREDS) =
//      for {
//        _ <- blobs
//              .toVector
//              .traverse((azure.relativize(from, _, to, secrets(to.account, to.container))).par)
//
//      } yield ()
//
//    def copy(from: ParsedPath, to: ParsedPath, secrets: CREDS) =
//      for {
//
//        tkn <- azure
//                .startListing(from.account, from.container, from.prefix, secrets(from.account, from.container))
//                .seq
//                .asProgramStep
//                .iterateUntilM()
//
//      } yield ()

    def runCommands(secrets: CREDS) =
      ActionInterpret2.interpret2[Seq[(OperationDescription, EvaluationSummary)]] {
        case Copy(from, to) => ???
        case Move(from, to) => ???
        case Remove(from)   => ???
        case Count(in)      => ???
      } _

    (for {
      cmd     <- EitherT.right[InvalidCommand](ui.promptCommand.seq.asProgramStep)
      expr    <- EitherT(parser.parseCommand(cmd).seq.asProgramStep)
      paths   = collectPaths(expr).flatten
      secrets <- EitherT.right[InvalidCommand](collectCreds(paths))
    } yield ()).value
  }
}

object Aaaa extends App {
  import cats.implicits._
  import cats._
  import cats.instances._
  import cats.syntax._

  def init() = Monad[Option].pure {
    println("init")
    1
  }

  def inc(v: Int) = Monad[Option].pure(v).map{x =>
    println(s"inc $x")
    x + 1
  }

  for {
    i <- init().iterateUntil(x => x > 3)
//    a <- inc(i)
//    b <- inc(i)
  } yield ()
}