package io.github.salamahin.azse3000
import cats.InjectK
import io.github.salamahin.azse3000.shared.{AzureFailure, Path}

package object blobstorage {
  sealed trait BlobStorageOps[T]
  final case class ListPage(inPath: Path, prev: Option[BlobsPage]) extends BlobStorageOps[Either[AzureFailure, BlobsPage]]
  final case class DownloadAttributes(blob: Blob)                  extends BlobStorageOps[Either[AzureFailure, Blob]]
  final case class StartCopying(src: Blob, dst: Blob)              extends BlobStorageOps[Either[AzureFailure, Unit]]
  final case class WaitForCopyStateUpdate()                        extends BlobStorageOps[Unit]

  final class BlobStorage[F[_]]()(implicit inj: InjectK[BlobStorageOps, F]) {
    def listPage(inPath: Path, prev: Option[BlobsPage]) = inj(ListPage(inPath, prev))
    def downloadAttributes(blob: Blob)                  = inj(DownloadAttributes(blob))
    def waitForCopyStateUpdate()                        = inj(WaitForCopyStateUpdate())
  }

  object BlobStorage {
    implicit def metafiless[F[_]](implicit inj: InjectK[BlobStorageOps, F]): BlobStorage[F] = new BlobStorage[F]
  }
}
