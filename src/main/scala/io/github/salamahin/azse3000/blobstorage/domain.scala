package io.github.salamahin.azse3000.blobstorage
import io.github.salamahin.azse3000.shared.AzureFailure

trait Page[P, B] {
  def blobs(page: P): Vector[B]
  def hasNext(page: P): Boolean
}

trait Blob[B] {
  def isCopied(blob: B): Either[AzureFailure, Boolean]
}

final case class CopyResult[B](copied: Vector[B], errors: Vector[AzureFailure])
