package io.github.salamahin.azse3000.blobstorage
import io.github.salamahin.azse3000.shared.AzureFailure

trait BlobsPage {
  def blobs: Seq[Blob]
  def hasNext: Boolean
}

trait Blob {
  def isCopied: Either[AzureFailure, Boolean]
}

final case class MappedBlob[T](src: Blob, mapped: T)
final case class CopyResult(copied: Vector[Blob], errors: Vector[AzureFailure])
