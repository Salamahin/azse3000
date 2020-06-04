package io.github.salamahin.azse3000.blobstorage
import cats.data.{EitherK, EitherT}
import cats.{InjectK, Monad}
import io.github.salamahin.azse3000.blobstorage.MetafilessBlobStorageProgram._
import io.github.salamahin.azse3000.shared.{AzureFailure, Path}

import scala.annotation.tailrec

sealed trait BlobStorageOps[T]
final case class ListPage(inPath: Path, prev: Option[BlobsPage2]) extends BlobStorageOps[Either[AzureFailure, BlobsPage2]]
final case class DownloadAttributes(blob: Blob)                   extends BlobStorageOps[Either[AzureFailure, Blob]]
final case class WaitForCopyStateUpdate()                         extends BlobStorageOps[Unit]

final class BlobStorage[F[_]]()(implicit inj: InjectK[BlobStorageOps, F]) {
  def listPage(inPath: Path, prev: Option[BlobsPage2]) = inj(ListPage(inPath, prev))
  def downloadAttributes(blob: Blob)                   = inj(DownloadAttributes(blob))
  def waitForCopyStateUpdate()                         = inj(WaitForCopyStateUpdate())
}

object BlobStorage {
  implicit def metafiless[F[_]](implicit inj: InjectK[BlobStorageOps, F]): BlobStorage[F] = new BlobStorage[F]
}

trait BlobsPage2 {
  def blobs: Seq[Blob]
  def hasNext: Boolean
}

trait Blob {
  def isCopied: Either[AzureFailure, Boolean]
}

final case class MappedBlob[T](src: Blob, mapped: T)
final case class CopyResult(copied: Vector[Blob], errors: Vector[AzureFailure])

object MetafilessBlobStorageProgram {
  final case class InnerCopyResult(copied: Vector[Blob], pending: Vector[Blob], errors: Vector[AzureFailure])
}

class MetafilessBlobStorageProgram[F[_]](implicit m: BlobStorage[EitherK[BlobStorageOps, F, *]]) {
  type Algebra[T] = EitherK[BlobStorageOps, F, T]

  import cats.implicits._
  import io.github.salamahin.azse3000.shared.Program._
  import m._

  def listAndProcessBlobs[T](from: Path)(f: Blob => Algebra[T]) = {
    def mapBlobs(thatPage: BlobsPage2, acc: Vector[MappedBlob[T]]) =
      thatPage
        .blobs
        .toVector
        .traverse(blob => f(blob).liftFA.map(MappedBlob(blob, _)))
        .map(x => acc ++ x)

    def mapThatPageAndListNext(thatPage: BlobsPage2, acc: Vector[MappedBlob[T]]) = {
      (listPage(from, Some(thatPage)).liftFA map2 mapBlobs(thatPage, acc)) {
        case (nextPage, mappedBlobs) => nextPage.map(page => (page, mappedBlobs))
      }
    }

    for {
      initPage <- listPage(from, None).liftFA.liftFree.toEitherT
      initAcc = Vector.empty[MappedBlob[T]]

      mapped <- Monad[EitherT[PRG[Algebra, *], AzureFailure, *]]
        .tailRecM(initPage, initAcc) {
          case (page, acc) =>
            if (page.hasNext)
              mapThatPageAndListNext(page, acc)
                .liftFree
                .toEitherT
                .map(_.asLeft[Vector[MappedBlob[T]]])
            else
              mapBlobs(page, acc)
                .liftFree
                .toRightEitherT[AzureFailure]
                .map(_.asRight[(BlobsPage2, Vector[MappedBlob[T]])])
        }

    } yield mapped
  }

  def waitUntilBlobsCopied(blobs: Vector[Blob]) = {
    @tailrec
    def separateBlobToCopiedPendingAndFailed(
      blobs: Vector[Blob],
      copied: Vector[Blob],
      pending: Vector[Blob],
      failed: Vector[AzureFailure]
    ): (Vector[Blob], Vector[Blob], Vector[AzureFailure]) =
      blobs match {
        case Vector() => (copied, pending, failed)
        case blob +: remaining =>
          blob.isCopied match {
            case Left(failure) => separateBlobToCopiedPendingAndFailed(remaining, copied, pending, failed :+ failure)
            case Right(true)   => separateBlobToCopiedPendingAndFailed(remaining, copied :+ blob, pending, failed)
            case Right(false)  => separateBlobToCopiedPendingAndFailed(remaining, copied, pending :+ blob, failed)
          }
      }

    def checkBlobsCopyState(of: Vector[Blob], acc: InnerCopyResult) =
      of
        .traverse(x => downloadAttributes(x).liftFA)
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
      .tailRecM((blobs, InnerCopyResult(Vector.empty, Vector.empty, Vector.empty))) {
        case (toCheck, previousCopyResult) =>
          checkBlobsCopyState(toCheck, previousCopyResult).liftFree
            .flatMap { checkResult =>
              val isPendingEmpty    = checkResult.pending.isEmpty
              val returnAccumulated = checkResult.asRight[(Vector[Blob], InnerCopyResult)].pureMonad[PRG[Algebra, *]]
              val nextIter          = (checkResult.pending, checkResult).asLeft[InnerCopyResult].pureMonad[PRG[Algebra, *]]

              if (isPendingEmpty) returnAccumulated else waitForCopyStateUpdate().liftFA.liftFree *> nextIter
            }
      }
      .map(icr => CopyResult(icr.copied, icr.errors))
  }
}
