package io.github.salamahin.azse3000
import cats.InjectK
import com.microsoft.azure.storage.blob.CloudBlockBlob
import io.github.salamahin.azse3000.shared.{AzureFailure, Path, Secret}

package object blobstorage {

  type LISTING_ATTEMPT           = Either[AzureFailure, ListingPage]
  type COPY_ATTEMPT              = Either[AzureFailure, CloudBlockBlob]
  type REMOVE_ATTEMPT            = Either[AzureFailure, Unit]
  type COPY_STATUS_CHECK_ATTEMPT = Either[AzureFailure, Boolean]

  sealed trait BlobStorageOps[T]
  final case class StartListing(inPath: Path, secret: Secret)                               extends BlobStorageOps[LISTING_ATTEMPT]
  final case class StartCopy(src: Path, blob: CloudBlockBlob, dst: Path, dstSecret: Secret) extends BlobStorageOps[COPY_ATTEMPT]
  final case class ContinueListing(prevPage: ListingPage)                                   extends BlobStorageOps[Option[ListingPage]]
  final case class IsCopied(blob: CloudBlockBlob)                                           extends BlobStorageOps[COPY_STATUS_CHECK_ATTEMPT]
  final case class RemoveBlob(blob: CloudBlockBlob)                                         extends BlobStorageOps[REMOVE_ATTEMPT]
  final case class SizeOfBlobBytes(blob: CloudBlockBlob)                                    extends BlobStorageOps[Long]

  final class BlobStorage[F[_]]()(implicit I: InjectK[BlobStorageOps, F]) {
    def startListing(inPath: Path, secret: Secret)                               = I(StartListing(inPath, secret))
    def continueListing(tkn: ListingPage)                                         = I(ContinueListing(tkn))
    def isCopied(blob: CloudBlockBlob)                                           = I(IsCopied(blob))
    def removeBlob(blob: CloudBlockBlob)                                         = I(RemoveBlob(blob))
    def sizeOfBlobBytes(blob: CloudBlockBlob)                                    = I(SizeOfBlobBytes(blob))
    def startCopy(src: Path, blob: CloudBlockBlob, dst: Path, dstSecret: Secret) = I(StartCopy(src, blob, dst, dstSecret))
  }

  object BlobStorage {
    implicit def blobStorage[F[_]](implicit I: InjectK[BlobStorageOps, F]): BlobStorage[F] = new BlobStorage[F]
  }
}
