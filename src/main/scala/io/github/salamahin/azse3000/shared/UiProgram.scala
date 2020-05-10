package io.github.salamahin.azse3000.shared

import cats.data.OptionT
import cats.free.Free._
import cats.free.FreeApplicative.FA
import cats.free.{Free, FreeApplicative}
import cats.{Monad, ~>}
import com.microsoft.azure.storage.ResultSegment
import com.microsoft.azure.storage.blob.{CloudBlockBlob, ListBlobItem}
import io.github.salamahin.azse3000.expression.ActionInterpret

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
    type PRG_STEP[A]   = Free[FA[F, *], A]
    type CREDS         = Map[(Account, Container), Secret]
    type SRC_DST_BLOBS = (CloudBlockBlob, CloudBlockBlob)

    val collectPaths =
      ActionInterpret.interpret[Seq[Path]] {
        case Copy(from, to) => from :+ to
        case Move(from, to) => from :+ to
        case Remove(from)   => from
        case Count(in)      => in
        case Size(in)       => in
      } _

    def collectCreds(paths: Seq[Path]) =
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

    def listAndProcessBlobs[T](from: Path, secret: Secret)(
      f: CloudBlockBlob => FreeApplicative[F, T]
    ) = {
      import scala.jdk.CollectionConverters._

      def mapBlobs(rs: ResultSegment[ListBlobItem]) =
        rs.getResults
          .asScala
          .map(_.asInstanceOf[CloudBlockBlob])
          .toVector
          .traverse(origBlob => //todo should be parallel
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
                    .map2(mappedBlobsOfThatSegment) { (newSegment, mappedBlobs) => //todo should be parallel
                      (newSegment, mappedBlobs).asLeft[Vector[(CloudBlockBlob, T)]]
                    }
                    .liftFree
            }
            .toRightEitherT[AzureFailure]

      } yield blobs
    }

    def waitUntilCopied(blobs: Vector[SRC_DST_BLOBS]) =
      Monad[PRG_STEP]
        .tailRecM(blobs, Vector.empty[SRC_DST_BLOBS], Vector.empty[AzureFailure]) {
          case (toCheck, totalSucceed, totalFailures) =>
            toCheck
              .traverse {
                case (srcBlob, dstBlob) => //todo parallel here?
                  azure
                    .isCopied(dstBlob)
                    .liftFA
                    .map { checkAttempt =>
                      (srcBlob, dstBlob) -> checkAttempt
                    }
              }
              .liftFree
              .flatMap { checks =>
                val (failed, checked) = checks.map {
                  case (_, Left(failure))             => failure.asLeft
                  case (srcAndDstBlobs, Right(true))  => (true, srcAndDstBlobs).asRight
                  case (srcAndDstBlobs, Right(false)) => (true, srcAndDstBlobs).asRight
                }.separate

                val (succeed, pending) = checked.partitionMap {
                  case (true, srcAndDst)  => srcAndDst.asLeft
                  case (false, srcAndDst) => srcAndDst.asRight
                }

                val newTotalSucceed  = totalSucceed ++ succeed
                val newTotalFailures = totalFailures ++ failed

                val iter: PRG_STEP[Either[
                  (Vector[SRC_DST_BLOBS], Vector[SRC_DST_BLOBS], Vector[AzureFailure]),
                  (Vector[SRC_DST_BLOBS], Vector[AzureFailure])
                ]] = if (pending.isEmpty) {
                  (newTotalSucceed, newTotalFailures)
                    .asRight[(Vector[SRC_DST_BLOBS], Vector[SRC_DST_BLOBS], Vector[AzureFailure])]
                    .pureMonad[PRG_STEP]
                } else {
                  concurrentController
                    .delayCopyStatusCheck()
                    .liftFA
                    .liftFree
                    .map { _ => (pending, newTotalSucceed, newTotalFailures).asLeft }
                }

                iter
              }
        }

    def listAndCopyBlobs(from: Path, to: Path, creds: CREDS) = {
      val listAndCopyAttempt = for {
        copyAttempts <- listAndProcessBlobs(from, creds(from.account, from.container)) {
          azure
            .startCopy(from, _, to, creds(to.account, to.container))
            .liftFA
        }

        (failedToInitiateCopy, initiatedCopies) = copyAttempts.map {
          case (_, Left(failure)) => failure.asLeft[(CloudBlockBlob, CloudBlockBlob)]
          case (src, Right(dst))  => (src, dst).asRight[AzureFailure]
        }.separate

        (successfullyCopied, checkStatusFailed) <- waitUntilCopied(initiatedCopies)
          .toRightEitherT[AzureFailure]

      } yield (successfullyCopied, failedToInitiateCopy ++ checkStatusFailed)

      listAndCopyAttempt.fold(
        listingFailed => (Vector.empty[SRC_DST_BLOBS], Vector(listingFailed)),
        identity
      )
    }

    def removeListedBlobs(blobs: Vector[CloudBlockBlob]) =
      blobs
        .traverse { b => //todo parallel here?
          azure.removeBlob(b).liftFA
        }
        .map { attempts =>
          val (failed, succeed) = attempts.separate
          (succeed.size, failed)
        }

    def moveAllBlobs(from: Seq[Path], to: Path, creds: CREDS) =
      from
        .toVector
        .traverse { f => //todo parallel here?
          val descr = Description(s"Move from $f to $to")

          (for {
            (successfulCopyAttempts, copyFailures) <- listAndCopyBlobs(f, to, creds)
            (copiedSourceBlobs, _) = successfulCopyAttempts.unzip
            (sucessfulyRemoved, removeFailures) <- removeListedBlobs(copiedSourceBlobs).liftFree

            report = InterpretationReport(descr, MoveSummary(sucessfulyRemoved), copyFailures ++ removeFailures)
          } yield report).liftFA
        }
        .fold

    def removeAllBlobs(from: Seq[Path], creds: CREDS) =
      from
        .toVector
        .traverse { f => //todo parallel here?
          val descr = Description(s"Remove from $f")

          listAndProcessBlobs(f, creds(f.account, f.container)) { blob => azure.removeBlob(blob).liftFA }
            .map { removings =>
              val (_, attempts)               = removings.unzip
              val (removingFailures, succeed) = attempts.separate

              InterpretationReport(descr, RemoveSummary(succeed.size), removingFailures)
            }
            .fold(
              failure => InterpretationReport(descr, RemoveSummary(0), Vector(failure)),
              identity
            )
            .liftFA
        }
        .fold

    def copyAllBlobs(from: Seq[Path], to: Path, creds: CREDS) =
      from
        .toVector
        .traverse { f => //todo parallel here?
          val descr = Description(s"Copy from $f to $to")

          listAndCopyBlobs(f, to, creds).map {
            case (succeed, failures) => InterpretationReport(descr, CopySummary(succeed.size), failures)
          }.liftFA
        }
        .fold

    def countAllBlobs(in: Seq[Path], creds: CREDS) =
      in.toVector
        .traverse { f => //todo parallel here?
          val descr = Description(s"Count in $f")

          listAndProcessBlobs(f, creds(f.account, f.container)) { _ => FreeApplicative.pure(1) }
            .map(listed => InterpretationReport(descr, CountSummary(listed.size), Vector.empty))
            .fold(
              failure => InterpretationReport(descr, CountSummary(0), Vector(failure)),
              identity
            )
            .liftFA
        }
        .fold

    def runActions(creds: CREDS) =
      ActionInterpret
        .interpret[PRG_STEP[Vector[InterpretationReport]]] {
          case Copy(from, to) => copyAllBlobs(from, to, creds)
          case Move(from, to) => moveAllBlobs(from, to, creds)
          case Remove(from)   => removeAllBlobs(from, creds)
          case Count(in)      => countAllBlobs(in, creds)
          case Size(in)       => ???
        } _

    (for {
      cmd  <-
        ui.promptCommand()
          .liftFA
          .liftFree
          .toRightEitherT[AzseException]

      expr <-
        parser
          .parseCommand(cmd)
          .liftFA
          .liftFree
          .toEitherT
          .leftWiden[AzseException]

      paths = collectPaths(expr).flatten

      secrets <- collectCreds(paths)
        .foldMap(λ[F ~> PRG_STEP](_.liftFA.liftFree))
        .toRightEitherT[AzseException]

      summary <- runActions(secrets)(expr)
        .traverse(identity)
        .toRightEitherT[AzseException]

    } yield summary).value

  }
}
