package io.github.salamahin.azse3000.blobstorage
import com.microsoft.azure.storage.blob.CloudBlockBlob

trait ListingPage {
  def blobs: Seq[CloudBlockBlob]
  def hasNext: Boolean
}
