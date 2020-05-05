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

    def listAndProcessBLobs[T](from: ParsedPath, to: ParsedPath, creds: CREDS)(
      f: CloudBlockBlob => ExecStrategy[F, T]
    ) = {
      import scala.jdk.CollectionConverters._

      def getBlobs(rs: ResultSegment[ListBlobItem]) =
        rs.getResults
          .asScala
          .map(_.asInstanceOf[CloudBlockBlob])
          .toVector
          .traverse(f(_).par) //fixme maybe parallel traverse required

      (for {
        initial <- EitherT(azure.startListing(from, creds((to.account, to.container))).seq.asProgramStep)
        blobs <- EitherT.right[FileSystemFailure] {
                  Monad[Program[F, *]].tailRecM(initial, Vector.empty[T]) {
                    case (segment, acc) =>
                      val newAcc = getBlobs(segment)
                      val token  = segment.getContinuationToken

                      if (token == null)
                        newAcc
                          .map(x => (x ++ acc).asRight[(ResultSegment[ListBlobItem], Vector[T])])
                          .asProgramStep
                      else
                        azure
                          .continueListing(token)
                          .par
                          .map2(newAcc) { (x, y) =>
                            (x, y).asLeft[Vector[T]]
                          }
                          .asProgramStep
                  }
                }
      } yield blobs).value
    }

    def separateCopyBlobStatus(blobs: Vector[(CloudBlockBlob, CloudBlockBlob, Either[FileSystemFailure, Boolean])]) = {
      def iter(
        bbs: Vector[(CloudBlockBlob, CloudBlockBlob, Either[Exception, Boolean])],
        failures: Vector[ActionFailed],
        pending: Vector[(CloudBlockBlob, CloudBlockBlob)],
        succeeds: Long
      ): (Vector[ActionFailed], Vector[(CloudBlockBlob, CloudBlockBlob)], Long) = {
        bbs match {
          case Vector.empty => (failures, pending, succeeds)

          case (_, _, Right(true)) +: remains =>
            iter(remains, failures, pending, succeeds + 1)

          case (fromBlob, toBlob, Right(false)) +: remains =>
            iter(remains, failures, (fromBlob, toBlob) +: pending, succeeds)

          case (fromBlob, toBlob, Left(failure)) +: remains =>
            val descr = s"Failed to copy ${fromBlob.getUri} to ${toBlob.getUri}"
            iter(remains, ActionFailed(descr, failure) +: failures, pending, succeeds)
        }
      }

      iter(blobs, Vector.empty, Vector.empty, 0)
    }

    def waitUntilCopied(blobs: Vector[CloudBlockBlob]) = {

//      Monad[Program[F, *]].tailRecM(blobs) { bbs =>
      //        bbs
      //          .traverse(azure.isCopied(_).par)
      //          .map { bb s =>
      ////            val (lefts, rights) = bbs.separate
      ////            val (pending, copied) = rights partition identity
      ////
      ////            if(pending.nonEmpty)
      //          }
      //      }
    }

    def copyAll(fromPaths: Seq[ParsedPath], to: ParsedPath, creds: CREDS) = {
      fromPaths
        .toVector
        .traverse { from =>
          listAndProcessBLobs(from, to, creds)(
            azure.startContentCopying(from, _, to, creds(to.account, to.container))
          )
        }
    }

    def runCommands(secrets: CREDS) =
      ActionInterpret2.interpret2[Program[F, (OperationDescription, EvaluationSummary)]] {
        case Copy(from, to) => copyAll(from, to, secrets)
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
