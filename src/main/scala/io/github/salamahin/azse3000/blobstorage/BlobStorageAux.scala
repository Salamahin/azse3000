package io.github.salamahin.azse3000.blobstorage
import io.github.salamahin.azse3000.shared.{AzureFailure, Path}

trait BlobStorageAux[F[_], P, B] {
  def listPage(inPath: Path, prev: Option[P]): F[Either[AzureFailure, P]]
  def downloadAttributes(blob: B): F[Either[AzureFailure, B]]
  def waitForCopyStateUpdate(): F[Unit]
}
