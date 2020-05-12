package io.github.salamahin.azse3000

import cats.data.{EitherT, OptionT}
import cats.free.Free._
import cats.free.FreeApplicative.FA
import cats.free.{Free, FreeApplicative}
import cats.{Functor, Monad, ~>}
import com.microsoft.azure.storage.blob.CloudBlockBlob
import io.github.salamahin.azse3000.expression.ActionInterpret
import io.github.salamahin.azse3000.shared.Azure.COPY_ATTEMPT
import io.github.salamahin.azse3000.shared._

object ProgramSyntax {
  implicit class LiftSyntax[F[_], A](fa: F[A]) {
    def liftFree: Free[F, A] = Free.liftF(fa)
    def liftFA: FA[F, A]     = FreeApplicative.lift(fa)
  }

  implicit class MonadSyntax[T](value: T) {
    def pureMonad[F[_]: Monad] = Monad[F].pure(value)
  }

  implicit class ToEitherTSyntax[F[_], L, R](either: F[Either[L, R]]) {
    def toEitherT = EitherT(either)
  }

  implicit class ToRightEitherTSyntax[F[_]: Functor, A](fa: F[A]) {
    def toRightEitherT[L] = EitherT.right[L](fa)
  }
}

object Program {

  import ProgramSyntax._
  import cats.instances.either._
  import cats.instances.vector._
  import cats.syntax.alternative._
  import cats.syntax.apply._
  import cats.syntax.bifunctor._
  import cats.syntax.either._
  import cats.syntax.traverse._

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

    def listAndProcessBlobs[T](from: Path, secret: Secret, descr: Description)(
      f: CloudBlockBlob => FA[F, T]
    ) = {
      for {
        initial <-
          azure
            .startListing(from, secret)
            .liftFA
            .liftFree
            .toEitherT

        blobs <-
          Monad[PRG_STEP]
            .tailRecM(Some(initial): Option[ListingPage], Vector.empty[(CloudBlockBlob, T)]) {
              case (Some(segment), acc) =>
                val mappedBlobsOfThatSegment = segment
                  .blobs
                  .toVector
                  .traverse(origBlob => //todo should be parallel
                    f(origBlob).map(mappedBlob => (origBlob, mappedBlob))
                  ) <* ui.showProgress(Description(s"[Listing...] ${descr.description}"), acc.size).liftFA

                azure
                  .continueListing(segment)
                  .liftFA
                  .map2(mappedBlobsOfThatSegment) { (newSegment, mappedBlobs) => //todo should be parallel
                    (newSegment, acc ++ mappedBlobs).asLeft[Vector[(CloudBlockBlob, T)]]
                  }
                  .liftFree

              case (None, acc) =>
                ui
                  .showProgress(descr, acc.size)
                  .liftFA
                  .liftFree
                  .map(_ => acc.asRight[(Option[ListingPage], Vector[(CloudBlockBlob, T)])])
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
                val (failed, checked) = checks
                  .map {
                    case (_, Left(failure))             => failure.asLeft
                    case (srcAndDstBlobs, Right(true))  => (true, srcAndDstBlobs).asRight
                    case (srcAndDstBlobs, Right(false)) => (true, srcAndDstBlobs).asRight
                  }
                  .separate

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

    def listAndCopyBlobs(from: Path, to: Path, creds: CREDS, descr: Description) = {
      val listAndCopyAttempt = for {

        copyAttempts <- listAndProcessBlobs[COPY_ATTEMPT](from, creds(from.account, from.container), descr) {
          azure
            .startCopy(from, _, to, creds(to.account, to.container))
            .liftFA
        }

        (failed, initiated) = copyAttempts
          .map {
            case (_, Left(failure)) => failure.asLeft[SRC_DST_BLOBS]
            case (src, Right(dst))  => (src, dst).asRight[AzureFailure]
          }
          .separate

        (successfullyCopied, checkStatusFailed) <- waitUntilCopied(initiated).toRightEitherT[AzureFailure]

      } yield (successfullyCopied, failed ++ checkStatusFailed)

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
            (successfulCopyAttempts, copyFailures) <- listAndCopyBlobs(f, to, creds, descr)
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

          listAndProcessBlobs(f, creds(f.account, f.container), descr) { blob => azure.removeBlob(blob).liftFA }
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

          listAndCopyBlobs(f, to, creds, descr)
            .map {
              case (succeed, failures) => InterpretationReport(descr, CopySummary(succeed.size), failures)
            }
            .liftFA
        }
        .fold

    def countAllBlobs(in: Seq[Path], creds: CREDS) =
      in.toVector
        .traverse { f => //todo parallel here?
          val descr = Description(s"Count in $f")

          listAndProcessBlobs(f, creds(f.account, f.container), descr) { _ => FreeApplicative.pure(1) }
            .map(listed => InterpretationReport(descr, CountSummary(listed.size), Vector.empty))
            .fold(
              failure => InterpretationReport(descr, CountSummary(0), Vector(failure)),
              identity
            )
            .liftFA
        }
        .fold

    def sizeOfAllBlobs(in: Seq[Path], creds: CREDS) = {
      in.toVector
        .traverse { f => //todo parallel here?
          val descr = Description(s"Size of blobs in $f")

          listAndProcessBlobs(f, creds(f.account, f.container), descr) { blob => azure.sizeOfBlobBytes(blob).liftFA }
            .map { fetchedSizes =>
              val (_, sizes) = fetchedSizes.unzip
              InterpretationReport(descr, SizeSummary(sizes.sum), Vector.empty)
            }
            .fold(
              failure => InterpretationReport(descr, SizeSummary(0), Vector(failure)),
              identity
            )
            .liftFA
        }
        .fold
    }

    def runActions(creds: CREDS) =
      ActionInterpret
        .interpret[PRG_STEP[Vector[InterpretationReport]]] {
          case Copy(from, to) => copyAllBlobs(from, to, creds)
          case Move(from, to) => moveAllBlobs(from, to, creds)
          case Remove(from)   => removeAllBlobs(from, creds)
          case Count(in)      => countAllBlobs(in, creds)
          case Size(in)       => sizeOfAllBlobs(in, creds)
        } _

    (for {
      cmd <- ui.promptCommand()
        .liftFA
        .liftFree
        .toRightEitherT[AzseException]

      expr <- parser
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
