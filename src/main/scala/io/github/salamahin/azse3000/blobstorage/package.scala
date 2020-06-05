package io.github.salamahin.azse3000
import cats.InjectK
import io.github.salamahin.azse3000.shared.{AzureFailure, Path}

package object blobstorage {
  sealed trait BlobStorageAuxOps[T, P, B]
  final case class ListPage[P, B](inPath: Path, prev: Option[P]) extends BlobStorageAuxOps[Either[AzureFailure, P], P, B]
  final case class DownloadAttributes[P, B](blob: B)             extends BlobStorageAuxOps[Either[AzureFailure, B], P, B]
  final case class WaitForCopyStateUpdate[P, B]()                extends BlobStorageAuxOps[Unit, P, B]

  final class BlobStorageAux[F[_], P, B]()(implicit inj: InjectK[BlobStorageAuxOps[*, P, B], F]) {
    def listPage(inPath: Path, prev: Option[P]) = inj(ListPage(inPath, prev))
    def downloadAttributes(blob: B)             = inj(DownloadAttributes(blob))
    def waitForCopyStateUpdate()                = inj(WaitForCopyStateUpdate())
  }

  object BlobStorageAux {
    implicit def metafiless[F[_], P, B](implicit inj: InjectK[BlobStorageAuxOps[*, P, B], F]): BlobStorageAux[F, P, B] = new BlobStorageAux[F, P, B]
  }
}
