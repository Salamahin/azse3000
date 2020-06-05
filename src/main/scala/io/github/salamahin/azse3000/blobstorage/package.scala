package io.github.salamahin.azse3000
import cats.InjectK
import io.github.salamahin.azse3000.shared.{AzureFailure, Path}

package object blobstorage {
  sealed trait BlobStorageOps[T, P, B]
  final case class ListPage[P, B](inPath: Path, prev: Option[P]) extends BlobStorageOps[Either[AzureFailure, P], P, B]
  final case class DownloadAttributes[P, B](blob: B)             extends BlobStorageOps[Either[AzureFailure, B], P, B]
  final case class WaitForCopyStateUpdate[P, B]()                extends BlobStorageOps[Unit, P, B]
  final case class StartCopying[P, B](src: B, dst: B)            extends BlobStorageOps[Either[AzureFailure, Unit], P, B]
  final case class Remove[P, B](blob: B)                         extends BlobStorageOps[Either[AzureFailure, Unit], P, B]

  final class BlobStorage[F[_], P, B]()(implicit inj: InjectK[BlobStorageOps[*, P, B], F]) {
    def listPage(inPath: Path, prev: Option[P]) = inj(ListPage(inPath, prev))
    def downloadAttributes(blob: B)             = inj(DownloadAttributes(blob))
    def waitForCopyStateUpdate()                = inj(WaitForCopyStateUpdate())
    def startCopying(src: B, dst: B)            = inj(StartCopying(src, dst))
    def remove(blob: B)                         = inj(Remove(blob))
  }

  object BlobStorage {
    implicit def metafiless[F[_], P, B](implicit inj: InjectK[BlobStorageOps[*, P, B], F]): BlobStorage[F, P, B] = new BlobStorage[F, P, B]
  }
}
