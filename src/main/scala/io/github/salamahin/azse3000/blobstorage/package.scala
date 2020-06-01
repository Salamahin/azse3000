package io.github.salamahin.azse3000
import cats.InjectK
import com.microsoft.azure.storage.blob.CloudBlockBlob
import io.github.salamahin.azse3000.shared.{AzureFailure, Path}

package object blobstorage {

  sealed trait BlobStorageOps[T]
  final case class StartListing(inPath: Path)                            extends BlobStorageOps[Either[AzureFailure, BlobsPage]]
  final case class ContinueListing(prevPage: BlobsPage)                extends BlobStorageOps[Option[BlobsPage]]

  final case class StartCopy(src: Path, blob: CloudBlockBlob, dst: Path) extends BlobStorageOps[Either[AzureFailure, CloudBlockBlob]]
  final case class IsCopied(blob: CloudBlockBlob)                        extends BlobStorageOps[Either[AzureFailure, Boolean]]
  final case class RemoveBlob(blob: CloudBlockBlob)                      extends BlobStorageOps[Either[AzureFailure, Unit]]
  final case class SizeOfBlobBytes(blob: CloudBlockBlob)                 extends BlobStorageOps[Long]

  final class BlobStorage[F[_]]()(implicit I: InjectK[BlobStorageOps, F]) {
    def startListing(inPath: Path)                            = I(StartListing(inPath))
    def continueListing(tkn: BlobsPage)                     = I(ContinueListing(tkn))
    def isCopied(blob: CloudBlockBlob)                        = I(IsCopied(blob))
    def removeBlob(blob: CloudBlockBlob)                      = I(RemoveBlob(blob))
    def sizeOfBlobBytes(blob: CloudBlockBlob)                 = I(SizeOfBlobBytes(blob))
    def startCopy(src: Path, blob: CloudBlockBlob, dst: Path) = I(StartCopy(src, blob, dst))
  }

  object BlobStorage {
    implicit def blobStorage[F[_]](implicit I: InjectK[BlobStorageOps, F]): BlobStorage[F] = new BlobStorage[F]
  }
}
