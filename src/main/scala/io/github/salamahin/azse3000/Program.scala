//package io.github.salamahin.azse3000
//
//import cats.Monad
//import cats.data.EitherK
//import cats.free.Free._
//import cats.free.FreeApplicative.FA
//import cats.free.{Free, FreeApplicative}
//import com.microsoft.azure.storage.blob.CloudBlockBlob
//import io.github.salamahin.azse3000.blobstorage._
//import io.github.salamahin.azse3000.delay.{DelayOps, Delays}
//import io.github.salamahin.azse3000.parsing.{Parser, ParsingOps}
//import io.github.salamahin.azse3000.shared._
//import io.github.salamahin.azse3000.ui.{UIOps, UserInterface}
//
//object Program {
//
//  import cats.instances.either._
//  import cats.instances.vector._
//  import cats.syntax.alternative._
//  import cats.syntax.bifunctor._
//  import cats.syntax.either._
//  import cats.syntax.traverse._
//
//  type App[A]        = EitherK[UIOps, EitherK[DelayOps, EitherK[BlobStorageOps, ParsingOps, *], *], A]
//  type SRC_DST_BLOBS = (CloudBlockBlob, CloudBlockBlob)
//
//  def apply(implicit ui: UserInterface[App], delays: Delays[App], parser: Parser[App], blobStorage: BlobStorage[App]) = {
//    def listAndProcessBlobs[T](from: Path, descr: Description)(
//      f: CloudBlockBlob => FA[App, T]
//    ) = {
//      import cats.implicits._
//      import cats.instances.vector._
//
//      for {
//        initial <- (blobStorage
//          .startListing(from)
//          .liftFree <* ui.showProgress(descr, 0, complete = false).liftFree)
//          .toEitherT
//
//        blobs <-
//          Monad[Free[App, *]]
//            .tailRecM(Some(initial): Option[ListingPage], Vector.empty[(CloudBlockBlob, T)]) {
//              case (Some(segment), acc) =>
//                val mappedBlobsOfThatSegment = segment
//                  .blobs
//                  .toVector
//                  .traverse(origBlob => //todo should be parallel
//                    f(origBlob).map(mappedBlob => (origBlob, mappedBlob))
//                  ) <* ui.showProgress(descr, acc.size, complete = false).liftFA
//
//                blobStorage
//                  .continueListing(segment)
//                  .liftFA
//                  .map2(mappedBlobsOfThatSegment) { (newSegment, mappedBlobs) => //todo should be parallel
//                    (newSegment, acc ++ mappedBlobs).asLeft[Vector[(CloudBlockBlob, T)]]
//                  }
//                  .monad
//
//              case (None, acc) =>
//                ui
//                  .showProgress(descr, acc.size, complete = true)
//                  .liftFree
//                  .map(_ => acc.asRight[(Option[ListingPage], Vector[(CloudBlockBlob, T)])])
//            }
//            .toRightEitherT[AzureFailure]
//
//      } yield blobs
//    }
//
//    def waitUntilCopied(blobs: Vector[SRC_DST_BLOBS]) =
//      Monad[Free[App, *]]
//        .tailRecM(blobs, Vector.empty[SRC_DST_BLOBS], Vector.empty[AzureFailure]) {
//          case (toCheck, totalSucceed, totalFailures) =>
//            toCheck
//              .traverse {
//                case (srcBlob, dstBlob) => //todo parallel here?
//                  blobStorage
//                    .isCopied(dstBlob)
//                    .liftFA
//                    .map { checkAttempt =>
//                      (srcBlob, dstBlob) -> checkAttempt
//                    }
//              }
//              .monad
//              .flatMap { checks =>
//                val (failed, checked) = checks
//                  .map {
//                    case (_, Left(failure))             => failure.asLeft
//                    case (srcAndDstBlobs, Right(true))  => (true, srcAndDstBlobs).asRight
//                    case (srcAndDstBlobs, Right(false)) => (true, srcAndDstBlobs).asRight
//                  }
//                  .separate
//
//                val (succeed, pending) = checked.partitionMap {
//                  case (true, srcAndDst)  => srcAndDst.asLeft
//                  case (false, srcAndDst) => srcAndDst.asRight
//                }
//
//                val newTotalSucceed  = totalSucceed ++ succeed
//                val newTotalFailures = totalFailures ++ failed
//
//                type ITER = Free[App, Either[
//                  (Vector[SRC_DST_BLOBS], Vector[SRC_DST_BLOBS], Vector[AzureFailure]),
//                  (Vector[SRC_DST_BLOBS], Vector[AzureFailure])
//                ]]
//
//                if (pending.isEmpty)
//                  (newTotalSucceed, newTotalFailures)
//                    .asRight[(Vector[SRC_DST_BLOBS], Vector[SRC_DST_BLOBS], Vector[AzureFailure])]
//                    .pureMonad[Free[App, *]]: ITER
//                else
//                  delays
//                    .delayCopyStatusCheck()
//                    .liftFree
//                    .map { _ => (pending, newTotalSucceed, newTotalFailures).asLeft }: ITER
//              }
//        }
//
//    def listAndCopyBlobs(from: Path, to: Path, descr: Description) = {
//      val listAndCopyAttempt = for {
//
//        copyAttempts <- listAndProcessBlobs(from, descr) {
//          blobStorage
//            .startCopy(from, _, to)
//            .liftFA
//        }
//
//        (failed, initiated) = copyAttempts
//          .map {
//            case (_, Left(failure)) => failure.asLeft[SRC_DST_BLOBS]
//            case (src, Right(dst))  => (src, dst).asRight[AzureFailure]
//          }
//          .separate
//
//        (successfullyCopied, checkStatusFailed) <- waitUntilCopied(initiated).toRightEitherT[AzureFailure]
//
//      } yield (successfullyCopied, failed ++ checkStatusFailed)
//
//      listAndCopyAttempt.fold(
//        listingFailed => (Vector.empty[SRC_DST_BLOBS], Vector(listingFailed)),
//        identity
//      )
//    }
//
//    def removeListedBlobs(blobs: Vector[CloudBlockBlob]) =
//      blobs
//        .traverse { b => //todo parallel here?
//          blobStorage.removeBlob(b).liftFA
//        }
//        .map { attempts =>
//          val (failed, succeed) = attempts.separate
//          (succeed.size, failed)
//        }
//
//    def moveAllBlobs(from: Seq[Path], to: Path) =
//      from
//        .toVector
//        .traverse { f => //todo parallel here?
//          val descr = Description(s"Move from $f to $to")
//
//          (for {
//            (successfulCopyAttempts, copyFailures) <- listAndCopyBlobs(f, to, descr)
//            (copiedSourceBlobs, _) = successfulCopyAttempts.unzip
//            (sucessfulyRemoved, removeFailures) <- removeListedBlobs(copiedSourceBlobs).monad
//
//            report = shared.InterpretationReport(descr, MoveSummary(sucessfulyRemoved), copyFailures ++ removeFailures)
//          } yield report).liftFA
//        }
//        .fold
//
//    def removeAllBlobs(from: Seq[Path]) =
//      from
//        .toVector
//        .traverse { f => //todo parallel here?
//          val descr = Description(s"Remove from $f")
//
//          listAndProcessBlobs(f, descr) { blob => blobStorage.removeBlob(blob).liftFA }
//            .map { removings =>
//              val (_, attempts)               = removings.unzip
//              val (removingFailures, succeed) = attempts.separate
//
//              shared.InterpretationReport(descr, RemoveSummary(succeed.size), removingFailures)
//            }
//            .fold(
//              failure => InterpretationReport(descr, RemoveSummary(0), Vector(failure)),
//              identity
//            )
//            .liftFA
//        }
//        .fold
//
//    def copyAllBlobs(from: Seq[Path], to: Path) =
//      from
//        .toVector
//        .traverse { f => //todo parallel here?
//          val descr = Description(s"Copy from $f to $to")
//
//          listAndCopyBlobs(f, to, descr)
//            .map {
//              case (succeed, failures) => shared.InterpretationReport(descr, CopySummary(succeed.size), failures)
//            }
//            .liftFA
//        }
//        .fold
//
//    def countAllBlobs(in: Seq[Path]) =
//      in.toVector
//        .traverse { f => //todo parallel here?
//          val descr = Description(s"Count in $f")
//
//          listAndProcessBlobs(f, descr) { _ => FreeApplicative.pure(1) }
//            .map(listed => InterpretationReport(descr, CountSummary(listed.size), Vector.empty))
//            .fold(
//              failure => InterpretationReport(descr, CountSummary(0), Vector(failure)),
//              identity
//            )
//            .liftFA
//        }
//        .fold
//
//    def sizeOfAllBlobs(in: Seq[Path]) = {
//      in.toVector
//        .traverse { f => //todo parallel here?
//          val descr = Description(s"Size of blobs in $f")
//
//          listAndProcessBlobs(f, descr) { blob => blobStorage.sizeOfBlobBytes(blob).liftFA }
//            .map { fetchedSizes =>
//              val (_, sizes) = fetchedSizes.unzip
//              InterpretationReport(descr, SizeSummary(sizes.sum), Vector.empty)
//            }
//            .fold(
//              failure => InterpretationReport(descr, SizeSummary(0), Vector(failure)),
//              identity
//            )
//            .liftFA
//        }
//        .fold
//    }
//
//    val runActions =
//      ActionInterpret
//        .interpret[Free[App, Vector[InterpretationReport]]] {
//          case Copy(from, to) => copyAllBlobs(from, to)
//          case Move(from, to) => moveAllBlobs(from, to)
//          case Remove(from)   => removeAllBlobs(from)
//          case Count(in)      => countAllBlobs(in)
//          case Size(in)       => sizeOfAllBlobs(in)
//        } _
//
//    (for {
//      cmd <- ui.promptCommand()
//        .liftFree
//        .toRightEitherT[AzseException]
//
//      expr <- parser
//        .parseCommand(cmd)
//        .liftFree
//        .toEitherT
//        .leftWiden[AzseException]
//
//      summary <- runActions(expr)
//        .traverse(identity)
//        .toRightEitherT[AzseException]
//
//      _ <- ui
//        .showReports(summary.flatten)
//        .liftFree
//        .toRightEitherT[AzseException]
//
//    } yield ()).value
//
//    ???
//  }
//}
