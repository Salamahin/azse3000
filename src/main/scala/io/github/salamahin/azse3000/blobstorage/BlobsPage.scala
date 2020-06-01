package io.github.salamahin.azse3000.blobstorage
import com.microsoft.azure.storage.blob.CloudBlockBlob

trait BlobsPage {
  def blobs: Seq[CloudBlockBlob]
  def hasNext: Boolean
}