package io.github.salamahin.azse3000.shared

import cats.data.OptionT
import cats.free.Free._
import cats.free.FreeApplicative.FA
import cats.free.{Free, FreeApplicative}
import cats.{Monad, ~>}
import com.microsoft.azure.storage.ResultSegment
import com.microsoft.azure.storage.blob.{CloudBlockBlob, ListBlobItem}
import io.github.salamahin.azse3000.expression.ActionInterpret
import io.github.salamahin.azse3000.shared.types.CREDS

import scala.annotation.tailrec

object UiProgram {
  import ExecStrategy._
  import cats.implicits._

  def program[F[_]: Monad](implicit
    ui: UserInterface[F],
    parser: Parser[F],
    configuration: Configuration[F],
    azure: AzureEngine[F],
    concurrentController: ConcurrentController[F]
  ) = {
    type SRC_DST_BLOBS    = (CloudBlockBlob, CloudBlockBlob)
    type SRC_DST_CHCK_RES = (CloudBlockBlob, CloudBlockBlob, Either[BlobCopyStatusCheckFailed, Boolean])
    type CHECK_ITER_ACCS  = (Vector[ActionFailed], Vector[SRC_DST_BLOBS], Long)
    type PRG_STEP[A]      = Free[FA[F, *], A]

    val collectPaths =
      ActionInterpret.interpret[Seq[ParsedPath]] {
        case Copy(from, to) => from :+ to
        case Move(from, to) => from :+ to
        case Remove(from)   => from
        case Count(in)      => in
      } _

    def mergeEithers[T, K](eithers: Vector[Either[T, K]]) = {
      val (failed, succeed) = eithers.separate

      if (failed.isEmpty) succeed.asRight[Vector[T]]
      else failed.asLeft[Vector[K]]
    }

    def runActions(creds: CREDS) =
      ActionInterpret.interpret[PRG_STEP[Either[Vector[ContainerListingFailed], Vector[(Description, Summary)]]]] {
        case Copy(from, to) => copyAllBlobs(from, to, creds)
        case Move(from, to) => ???
        case Remove(from)   => ???
        case Count(in)      => ???
      } _

    def collectCreds(paths: Seq[ParsedPath]) =
      paths
        .groupBy(p => (p.account, p.container))
        .keys
        .toVector
        .traverse {
          case id @ (acc, cont) =>
            OptionT(Free.liftF(configuration.readCreds(acc, cont)))
              .getOrElseF(Free.liftF(ui.promptCreds(acc, cont)))
              .map(secret => id -> secret)
        }
        .map(_.toMap)

    def listAndProcessBlobs[T](from: ParsedPath, secret: Secret)(
      f: CloudBlockBlob => FreeApplicative[F, T] //fixme F[T]?
    ) = {
      import scala.jdk.CollectionConverters._

      def mapBlobs(rs: ResultSegment[ListBlobItem]) =
        rs.getResults
          .asScala
          .map(_.asInstanceOf[CloudBlockBlob])
          .toVector
          .traverse(origBlob => //todo parallel here?
            f(origBlob).map(mappedBlob => (origBlob, mappedBlob))
          )

      for {
        initial <-
          azure
            .startListing(from, secret)
            .liftFA
            .liftFree
            .toEitherT

        blobs   <-
          Monad[PRG_STEP]
            .tailRecM(initial, Vector.empty[(CloudBlockBlob, T)]) {
              case (segment, acc) =>
                val mappedBlobsOfThatSegment = mapBlobs(segment)
                val token                    = segment.getContinuationToken

                if (token == null)
                  mappedBlobsOfThatSegment
                    .map(x => (x ++ acc).asRight[(ResultSegment[ListBlobItem], Vector[(CloudBlockBlob, T)])])
                    .liftFree
                else
                  azure
                    .continueListing(token)
                    .liftFA
                    .map2(mappedBlobsOfThatSegment) { (newSegment, mappedBlobs) =>
                      (newSegment, mappedBlobs).asLeft[Vector[(CloudBlockBlob, T)]]
                    }
                    .liftFree
            }
            .toRightEitherT[ContainerListingFailed]

      } yield blobs
    }

    def separateCopyStatusResults(originalAndCopies: Vector[SRC_DST_CHCK_RES]) = {
      @tailrec
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

    def waitUntilCopied(originalAndCopies: Vector[SRC_DST_BLOBS])          =
      Monad[PRG_STEP]
        .tailRecM(Vector.empty[ActionFailed], originalAndCopies, 0: Long) {
          case (totalFailures, bbs, totalSucceeds) =>
            bbs
              .traverse { //todo parallel here?
                case (original, copied) =>
                  azure
                    .isCopied(copied)
                    .liftFA
                    .map(result => (original, copied, result))
              }
              .map(separateCopyStatusResults)
              .liftFree
              .flatMap {
                case (failures, pending, succeeds) =>
                  if (pending.isEmpty)
                    Summary(succeeds + totalSucceeds, failures ++ totalFailures)
                      .asRight[CHECK_ITER_ACCS]
                      .pureMonad[PRG_STEP]
                  else
                    concurrentController
                      .delayCopyStatusCheck()
                      .liftFA
                      .map { _ =>
                        (failures ++ totalFailures, pending, succeeds + totalSucceeds).asLeft[Summary]
                      }
                      .liftFree
              }
        }

    // format: off
    def listAndCopyBlobs(from: ParsedPath, to: ParsedPath, secrets: CREDS) =
      (for {
        copyAttempts <- listAndProcessBlobs(
          from,
          secrets(from.account, from.container)
        ) {
          azure
            .startContentCopying(from, _, to, secrets(to.account, to.container))
            .liftFA
        }

        (failedToStartCopyRoutines, startedCopyRoutines) = copyAttempts
          .map {
            case (src, Left(failure)) =>
              ActionFailed(s"Failed to initiate a copying of blob ${src.getUri} to $to", failure).asLeft

            case (src, Right(dst)) => (src, dst).asRight
          }
          .separate

        summary <- waitUntilCopied(startedCopyRoutines)
          .map(copyStats => copyStats.copy(errors = copyStats.errors ++ failedToStartCopyRoutines))
          .toRightEitherT[ContainerListingFailed]

      } yield Description(s"Copy from $from to $to") -> summary).value
    // format: on

    def copyAllBlobs(from: Seq[ParsedPath], to: ParsedPath, secrets: CREDS) =
      from
        .toVector
        .traverse(x => FreeApplicative.lift(listAndCopyBlobs(x, to, secrets))) //todo parallel here?
        .map(mergeEithers)
        .fold



    (for {
      cmd  <-
        ui.promptCommand()
          .liftFA
          .liftFree
          .toRightEitherT[AzException with Fatal]

      expr <-
        parser
          .parseCommand(cmd)
          .liftFA
          .liftFree
          .toEitherT
          .leftWiden[AzException with Fatal]

      paths = collectPaths(expr).flatten

      secrets <- collectCreds(paths)
        .foldMap(λ[F ~> PRG_STEP](_.liftFA.liftFree))
        .toRightEitherT[AzException with Fatal]

      summary <-
        runActions(secrets)(expr)
          .traverse(identity)
          .map(mergeEithers)
          .map(
            _.bimap(
              problems => AggregatedFatals(problems.flatten),
              summary => summary.flatten.toMap
            )
          )
          .toEitherT
          .leftWiden[AzException with Fatal]

    } yield summary).value
  }
}
