package io.github.salamahin.azse3000.blobstorage
import cats.data.EitherT
import cats.{InjectK, Monad, Monoid}
import com.microsoft.azure.storage.blob.{CloudBlockBlob, CopyStatus}
import io.github.salamahin.azse3000.shared.{AzureFailure, Path}

import scala.annotation.tailrec

sealed trait MetafilelessOps[T]
final case class StartListing2(inPath: Path)              extends MetafilelessOps[Either[AzureFailure, ListingPage]]
final case class ContinueListing2(prevPage: ListingPage)  extends MetafilelessOps[Either[AzureFailure, ListingPage]]
final case class DownloadAttributes(blob: CloudBlockBlob) extends MetafilelessOps[Either[AzureFailure, CloudBlockBlob]]

final class Metafileless[F[_]]()(implicit inj: InjectK[MetafilelessOps, F]) {
  def initPage(inPath: Path)                   = inj(StartListing2(inPath))
  def nextPage(tkn: ListingPage)               = inj(ContinueListing2(tkn))
  def downloadAttributes(blob: CloudBlockBlob) = inj(DownloadAttributes(blob))
}

object Metafileless {
  implicit def metafiless[F[_]](implicit inj: InjectK[MetafilelessOps, F]): Metafileless[F] = new Metafileless[F]
}

final case class MappedBlob[T](src: CloudBlockBlob, mapped: T)
final case class CopyResult(copied: Vector[CloudBlockBlob], errors: Vector[AzureFailure])

object MetafilessBlobStorageProgram {

  final case class InnerCopyResult(copied: Vector[CloudBlockBlob], pending: Vector[CloudBlockBlob], errors: Vector[AzureFailure])

  implicit val copyResultMonoid = new Monoid[InnerCopyResult] {
    override def empty                                           = InnerCopyResult(Vector.empty, Vector.empty, Vector.empty)
    override def combine(x: InnerCopyResult, y: InnerCopyResult) = InnerCopyResult(x.copied ++ y.copied, x.pending ++ y.pending, x.errors ++ y.errors)
  }

  type Algebra[T] = MetafilelessOps[T]
  def program(implicit m: Metafileless[Algebra]) = {
    import cats.implicits._
    import io.github.salamahin.azse3000.shared.Program._
    import m._

    def listAndProcessBlobs[T](from: Path)(f: CloudBlockBlob => Algebra[T]) = {
      def mapBlobs(thatPage: ListingPage, acc: Vector[MappedBlob[T]]) =
        thatPage
          .blobs
          .toVector
          .traverse(blob => f(blob).liftFA.map(MappedBlob(blob, _)))
          .map(x => acc ++ x)

      def mapThatPageAndListNext(thatPage: ListingPage, acc: Vector[MappedBlob[T]]) = {
        (nextPage(thatPage).liftFA map2 mapBlobs(thatPage, acc)) {
          case (nextPage, mappedBlobs) => nextPage.map(page => (page, mappedBlobs))
        }
      }

      for {
        initPage <- initPage(from).liftFA.liftFree.toEitherT
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
                  .map(_.asRight[(ListingPage, Vector[MappedBlob[T]])])
          }

      } yield mapped
    }

    def waitUntilBlobsCopied(blobs: Vector[CloudBlockBlob]) = {
      @tailrec
      def separateBlobToCopiedPendingAndFailed(
        blobs: Vector[CloudBlockBlob],
        copied: Vector[CloudBlockBlob],
        pending: Vector[CloudBlockBlob],
        failed: Vector[AzureFailure]
      ): (Vector[CloudBlockBlob], Vector[CloudBlockBlob], Vector[AzureFailure]) =
        blobs match {
          case Vector() => (copied, pending, failed)
          case blob +: remaining =>
            val state  = blob.getCopyState
            val status = state.getStatus

            if (status == CopyStatus.SUCCESS)
              (copied :+ blob, pending, failed)
            else if (status == CopyStatus.PENDING)
              (copied, pending :+ blob, failed)
            else {
              val msg     = s"Unexpected copy status of blob ${blob.getUri}: $status"
              val failure = AzureFailure(msg, new IllegalStateException(state.getStatusDescription))

              separateBlobToCopiedPendingAndFailed(remaining, copied, pending, failed :+ failure)
            }
        }

      def checkBlobsCopyState(of: Vector[CloudBlockBlob]) =
        of
          .traverse(x => downloadAttributes(x).liftFA)
          .map { blobs =>
            val (downloadAttributesFailed, checked) = blobs.separate
            val (copied, pending, copyFailed)       = separateBlobToCopiedPendingAndFailed(checked, Vector.empty, Vector.empty, Vector.empty)

            InnerCopyResult(copied, pending, downloadAttributesFailed ++ copyFailed)
          }

      Monad[PRG[Algebra, *]]
        .tailRecM((blobs, Vector.empty[InnerCopyResult])) {
          case (toCheck, acc) =>
            for {
              result @ InnerCopyResult(_, pending, _) <- checkBlobsCopyState(toCheck).liftFree
            } yield {
              if (pending.isEmpty) (acc :+ result).asRight[(Vector[CloudBlockBlob], Vector[InnerCopyResult])]
              else (pending, acc :+ result).asLeft[Vector[InnerCopyResult]]
            }
        }
        .map { Monoid[InnerCopyResult].combineAll }
        .map(icr => CopyResult(icr.copied, icr.errors))
    }
  }
}
