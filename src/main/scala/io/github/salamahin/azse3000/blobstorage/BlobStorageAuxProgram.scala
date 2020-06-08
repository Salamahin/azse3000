package io.github.salamahin.azse3000.blobstorage
import cats.data.EitherT
import cats.{Applicative, Monad, Parallel}
import io.github.salamahin.azse3000.blobstorage.BlobStorageAuxProgram._
import io.github.salamahin.azse3000.shared.{AzureFailure, Path}

import scala.annotation.tailrec

private object BlobStorageAuxProgram {
  final case class InnerCopyResult[B](copied: Vector[B], pending: Vector[B], errors: Vector[AzureFailure])
}

class BlobStorageAuxProgram[F[_]: Monad: Applicative: Parallel, P, B](aux: BlobStorageAux[F, P, B])(implicit page: Page[P, B], blob: Blob[B]) {
  type MAPPED_BLOB[T] = (B, T)

  import aux._
  import blob._
  import cats.instances.either._
  import cats.instances.vector._
  import cats.syntax.alternative._
  import cats.syntax.apply._
  import cats.syntax.either._
  import cats.syntax.flatMap._
  import cats.syntax.functor._
  import cats.syntax.parallel._
  import io.github.salamahin.azse3000.shared.Program._
  import page._

  def listAndProcessBlobs[T](from: Path)(f: B => F[T]) = {
    def mapBlobs(thatPage: P, acc: Vector[MAPPED_BLOB[T]]) =
      blobs(thatPage)
        .parTraverse(src => f(src).map(dst => (src, dst)))
        .map(x => acc ++ x)

    def mapThatPageAndListNext(thatPage: P, acc: Vector[MAPPED_BLOB[T]]) = {
      Parallel.parMap2(listPage(from, Some(thatPage)), mapBlobs(thatPage, acc)) {
        case (nextPage, mappedBlobs) => nextPage.map(page => (page, mappedBlobs))
      }
    }

    for {
      initPage <- listPage(from, None).toEitherT
      initAcc = Vector.empty[MAPPED_BLOB[T]]

      mapped <- Monad[EitherT[F, AzureFailure, *]]
        .tailRecM(initPage, initAcc) {
          case (page, acc) =>
            if (hasNext(page))
              mapThatPageAndListNext(page, acc)
                .toEitherT
                .map(_.asLeft[Vector[MAPPED_BLOB[T]]])
            else
              mapBlobs(page, acc)
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
        .parTraverse(x => downloadAttributes(x))
        .map { blobs =>
          val (downloadAttributesFailed, checked) = blobs.separate
          val (copied, pending, copyFailed)       = separateBlobToCopiedPendingAndFailed(checked, Vector.empty, Vector.empty, Vector.empty)

          acc.copy(
            copied = acc.copied ++ copied,
            pending = pending,
            errors = acc.errors ++ downloadAttributesFailed ++ copyFailed
          )
        }

    Monad[F]
      .tailRecM((blobs, InnerCopyResult[B](Vector.empty, Vector.empty, Vector.empty))) {
        case (toCheck, previousCopyResult) =>
          checkBlobsCopyState(toCheck, previousCopyResult)
            .flatMap { checkResult =>
              val isPendingEmpty    = checkResult.pending.isEmpty
              val returnAccumulated = checkResult.asRight[(Vector[B], InnerCopyResult[B])].pureMonad[F]
              val nextIter          = (checkResult.pending, checkResult).asLeft[InnerCopyResult[B]].pureMonad[F]

              if (isPendingEmpty) returnAccumulated else waitForCopyStateUpdate() *> nextIter
            }
      }
      .map(icr => CopyResult(icr.copied, icr.errors))
  }
}
