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
    type SRC_DST_BLOBS    = (CloudBlockBlob, CloudBlockBlob)
    type SRC_DST_CHCK_RES = (CloudBlockBlob, CloudBlockBlob, Either[Exception, Boolean])
    type CHECK_ITER_ACCS  = (Vector[ActionFailed], Vector[SRC_DST_BLOBS], Long)

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

      def mapBlobs(rs: ResultSegment[ListBlobItem]) =
        rs.getResults
          .asScala
          .map(_.asInstanceOf[CloudBlockBlob])
          .toVector
          .traverse(origBlob => f(origBlob).par.map(mappedBlob => (origBlob, mappedBlob))) //fixme maybe parallel traverse required

      (for {
        initial <- EitherT(azure.startListing(from, creds((to.account, to.container))).seq.asProgramStep)
        blobs <- EitherT.right[FileSystemFailure] {
                  Monad[Program[F, *]].tailRecM(initial, Vector.empty[(CloudBlockBlob, T)]) {
                    case (segment, acc) =>
                      val mappedBlobsOfThatSegment = mapBlobs(segment)
                      val token                    = segment.getContinuationToken

                      if (token == null)
                        mappedBlobsOfThatSegment
                          .map(x => (x ++ acc).asRight[(ResultSegment[ListBlobItem], Vector[(CloudBlockBlob, T)])])
                          .asProgramStep
                      else
                        azure
                          .continueListing(token)
                          .par
                          .map2(mappedBlobsOfThatSegment) { (newSegment, mappedBlobs) =>
                            (newSegment, mappedBlobs).asLeft[Vector[(CloudBlockBlob, T)]]
                          }
                          .asProgramStep
                  }
                }
      } yield blobs).value
    }

    def separateCopyStatusResults(originalAndCopies: Vector[SRC_DST_CHCK_RES]) = {
      def iter(
        bbs: Vector[SRC_DST_CHCK_RES],
        failures: Vector[ActionFailed],
        pending: Vector[SRC_DST_BLOBS],
        succeeds: Long
      ): CHECK_ITER_ACCS = {
        bbs match {
          case Vector() => (failures, pending, succeeds)

          case (_, _, Right(true)) +: remains =>
            iter(remains, failures, pending, succeeds + 1)

          case (fromBlob, toBlob, Right(false)) +: remains =>
            iter(remains, failures, (fromBlob, toBlob) +: pending, succeeds)

          case (fromBlob, toBlob, Left(failure)) +: remains =>
            val descr = s"Failed to copy ${fromBlob.getUri} to ${toBlob.getUri}"
            iter(remains, ActionFailed(descr, failure) +: failures, pending, succeeds)
        }
      }

      iter(originalAndCopies, Vector.empty, Vector.empty, 0)
    }

    def delayCopyStatusCheck[T](result: T) =
      concurrentController
        .delayCopyStatusCheck()
        .seq
        .asProgramStep
        .map(_ => result)

    def waitUntilCopied(originalAndCopies: Vector[SRC_DST_BLOBS]) = {
      Monad[Program[F, *]].tailRecM(Vector.empty[ActionFailed], originalAndCopies, 0: Long) {
        case (totalFailures, bbs, totalSucceeds) =>
          bbs
            .traverse {
              case (original, copied) => azure.isCopied(copied).par.map(result => (original, copied, result))
            }
            .map { checked =>
              separateCopyStatusResults(checked)
            }
            .asProgramStep
            .flatMap {
              case (failures, pending, succeeds) =>
                if (pending.isEmpty) {
                  Monad[Program[F, *]].pure {
                    EvaluationSummary(succeeds + totalSucceeds, failures ++ totalFailures).asRight[CHECK_ITER_ACCS]
                  }
                } else {
                  delayCopyStatusCheck(failures ++ totalFailures, pending, succeeds + totalSucceeds)
                    .map(_.asLeft[EvaluationSummary])
                }
            }
      }
    }

//    def copyAll(fromPaths: Seq[ParsedPath], to: ParsedPath, creds: CREDS) = {
//      fromPaths
//        .toVector
//        .traverse { from => //todo maybe traverse par here
//          listAndProcessBLobs(from, to, creds)(
//            azure.startContentCopying(from, _, to, creds(to.account, to.container))
//          )
//        }
//    }

//    def runCommands(secrets: CREDS) =
//      ActionInterpret2.interpret2[Program[F, (OperationDescription, EvaluationSummary)]] {
//        case Copy(from, to) => copyAll(from, to, secrets)
//        case Move(from, to) => ???
//        case Remove(from)   => ???
//        case Count(in)      => ???
//      } _

    (for {
      cmd     <- EitherT.right[InvalidCommand](ui.promptCommand.seq.asProgramStep)
      expr    <- EitherT(parser.parseCommand(cmd).seq.asProgramStep)
      paths   = collectPaths(expr).flatten
      secrets <- EitherT.right[InvalidCommand](collectCreds(paths))
    } yield ()).value
  }
}
