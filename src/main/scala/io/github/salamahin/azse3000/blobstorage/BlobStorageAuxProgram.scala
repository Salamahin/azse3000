package io.github.salamahin.azse3000.blobstorage
import cats.Monad
import cats.data.EitherT
import cats.free.{Free, FreeApplicative}
import cats.free.FreeApplicative.FA
import io.github.salamahin.azse3000.blobstorage.BlobStorageAuxProgram._
import io.github.salamahin.azse3000.shared.{AzureFailure, Path}

import scala.annotation.tailrec

private object BlobStorageAuxProgram {
  final case class InnerCopyResult[B](copied: Vector[B], pending: Vector[B], errors: Vector[AzureFailure])
}

trait BlobStorageAuxProgram[F[_], P, B](implicit
  m: BlobStorageAux[BlobStorageAuxOps[*, P, B], P, B],
  p: Page[P, B],
  b: Blob[B]
) {
  type Algebra[T]     = BlobStorageAuxOps[T, P, B]
  type MAPPED_BLOB[T] = (B, T)

  import b._
  import cats.instances.either._
  import cats.instances.vector._
  import cats.syntax.alternative._
  import cats.syntax.apply._
  import cats.syntax.either._
  import cats.syntax.traverse._
  import io.github.salamahin.azse3000.shared.Program._
  import m._
  import p._

  def listAndProcessBlobs[T](from: Path)(f: B => Algebra[T]) = {
    def mapBlobs(thatPage: P, acc: Vector[MAPPED_BLOB[T]]) =
      blobs(thatPage)
        .traverse(src => f(src).liftFA.map(dst => (src, dst)))
        .map(x => acc ++ x)

    def mapThatPageAndListNext(thatPage: P, acc: Vector[MAPPED_BLOB[T]]) =
      (FreeApplicative.lift[Algebra, Either[AzureFailure, P]](listPage(from, Some(thatPage))) map2 mapBlobs(thatPage, acc)) {
        case (nextPage, mappedBlobs) => nextPage.map(page => (page, mappedBlobs))
      }

    for {
      initPage <- FreeApplicative.lift[Algebra, Either[AzureFailure, P]](listPage(from, None)).liftFree.toEitherT
      initAcc = Vector.empty[MAPPED_BLOB[T]]

      mapped <- Monad[EitherT[PRG[Algebra, *], AzureFailure, *]]
        .tailRecM(initPage, initAcc) {
          case (page, acc) =>
            if (hasNext(page))
              mapThatPageAndListNext(page, acc)
                .liftFree
                .toEitherT
                .map(_.asLeft[Vector[MAPPED_BLOB[T]]])
            else
              mapBlobs(page, acc)
                .liftFree
                .toRightEitherT[AzureFailure]
                .map(_.asRight[(P, Vector[MAPPED_BLOB[T]])])
        }

    } yield mapped
  }

  def waitUntilBlobsCopied(blobs: Vector[B]) = {
    @tailrec
    def separateBlobToCopiedPendingAndFailed(
      blobs: Vector[B],
      copied: Vector[B],
      pending: Vector[B],
      failed: Vector[AzureFailure]
    ): (Vector[B], Vector[B], Vector[AzureFailure]) =
      blobs match {
        case Vector() => (copied, pending, failed)
        case blob +: remaining =>
          isCopied(blob) match {
            case Left(failure) => separateBlobToCopiedPendingAndFailed(remaining, copied, pending, failed :+ failure)
            case Right(true)   => separateBlobToCopiedPendingAndFailed(remaining, copied :+ blob, pending, failed)
            case Right(false)  => separateBlobToCopiedPendingAndFailed(remaining, copied, pending :+ blob, failed)
          }
      }

    def checkBlobsCopyState(of: Vector[B], acc: InnerCopyResult[B]) =
      of
        .traverse(x => FreeApplicative.lift[Algebra, Either[AzureFailure, B]](downloadAttributes(x)))
        .map { blobs =>
          val (downloadAttributesFailed, checked) = blobs.separate
          val (copied, pending, copyFailed)       = separateBlobToCopiedPendingAndFailed(checked, Vector.empty, Vector.empty, Vector.empty)

          acc.copy(
            copied = acc.copied ++ copied,
            pending = pending,
            errors = acc.errors ++ downloadAttributesFailed ++ copyFailed
          )
        }

    Monad[PRG[Algebra, *]]
      .tailRecM((blobs, InnerCopyResult[B](Vector.empty, Vector.empty, Vector.empty))) {
        case (toCheck, previousCopyResult) =>
          Free.liftF[FA[Algebra, *], InnerCopyResult[B]](checkBlobsCopyState(toCheck, previousCopyResult))
            .flatMap { checkResult =>
              val isPendingEmpty    = checkResult.pending.isEmpty
              val returnAccumulated = checkResult.asRight[(Vector[B], InnerCopyResult[B])].pureMonad[PRG[Algebra, *]]
              val nextIter          = (checkResult.pending, checkResult).asLeft[InnerCopyResult[B]].pureMonad[PRG[Algebra, *]]

              if (isPendingEmpty) returnAccumulated
              else Free.liftF(FreeApplicative.lift[Algebra, Unit](waitForCopyStateUpdate())) *> nextIter
            }
      }
      .map(icr => CopyResult(icr.copied, icr.errors))
  }
}
