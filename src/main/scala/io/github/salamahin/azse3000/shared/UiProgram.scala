package io.github.salamahin.azse3000.shared

import cats.Monad
import cats.data.{EitherT, OptionT}
import cats.free.Free._
import com.microsoft.azure.storage.ResultSegment
import com.microsoft.azure.storage.blob.{CloudBlockBlob, ListBlobItem}
import io.github.salamahin.azse3000.expression.ActionInterpret2
import io.github.salamahin.azse3000.shared.types.CREDS

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

    def startBlobCopying(from: ParsedPath, fromBlob: CloudBlockBlob, to: ParsedPath, secrets: CREDS) =
      for {
        toBlob <- EitherT(azure.relativize(from, fromBlob, to, secrets(to.account, to.container)).seq)
        _      = toBlob.startCopy(fromBlob) //fixme use free
      } yield toBlob

    def listAndProcessBLobs[T](rs: ResultSegment[ListBlobItem])(f: CloudBlockBlob => ExecStrategy[F, T]) = {
      import scala.jdk.CollectionConverters._

      Monad[Program[F, *]].tailRecM(rs, Vector.empty[T]) {
        case (segment, acc) =>
          val allBlobs = rs
            .getResults
            .asScala
            .map(_.asInstanceOf[CloudBlockBlob])
            .toVector
            .traverse(f(_).par)
            .map(acc ++ _)
            .asProgramStep

          val token = segment.getContinuationToken

          if (token == null) allBlobs.map(_.asRight[(ResultSegment[ListBlobItem], Vector[T])])
          else
            for {
              nextToken <- azure.continueListing(token).seq.asProgramStep
              bbs       <- allBlobs
              next      = (nextToken, bbs).asLeft[Vector[T]]
            } yield next
      }
    }

    def startListing(from: ParsedPath, to: ParsedPath, creds: CREDS) = {
      import scala.jdk.CollectionConverters._

      for {
        tkn <- EitherT(azure.startListing(from, creds((to.account, to.container))).seq)
        blobs = tkn
          .getResults
          .asScala
          .map(_.asInstanceOf[CloudBlockBlob])

      } yield ()
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
