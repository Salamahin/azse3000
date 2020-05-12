package io.github.salamahin.azse3000

import java.net.URI

import cats.~>
import com.microsoft.azure.storage.blob.{CloudBlobContainer, CloudBlockBlob, ListBlobItem}
import com.microsoft.azure.storage.{ResultSegment, StorageCredentialsSharedAccessSignature}
import io.github.salamahin.azse3000.shared._
import zio.{Task, UIO, URIO}

object AzureEngineInterpreter extends (Azure ~> UIO) {

  private def listBlobs(cont: CloudBlobContainer, prefix: Prefix, rs: Option[ResultSegment[ListBlobItem]]) =
    cont.listBlobsSegmented(
      prefix.path,
      true,
      null,
      5000,
      rs.map(_.getContinuationToken).orNull,
      null,
      null
    )

  class ListingPageImpl(cont: CloudBlobContainer, prefix: Prefix, rs: ResultSegment[ListBlobItem]) extends ListingPage {
    import scala.jdk.CollectionConverters._

    override def blobs: Seq[CloudBlockBlob] =
      rs.getResults
        .asScala
        .map(b => b.asInstanceOf[CloudBlockBlob])
        .toSeq

    override def next: Option[ListingPage] =
      if (!rs.getHasMoreResults) None
      else Some(new ListingPageImpl(cont, prefix, listBlobs(cont, prefix, Some(rs))))
  }

  private def startListing(inPath: Path, secret: Secret) = {
    val t = Task {
      val c = new CloudBlobContainer(
        URI.create(s"https://${inPath.account}.blob.core.windows.net/${inPath.container}"),
        new StorageCredentialsSharedAccessSignature(secret.secret)
      )

      new ListingPageImpl(c, inPath.prefix, listBlobs(c, inPath.prefix, None))
    }

    t.mapError(th => AzureFailure(s"Failed to list blobs in $inPath (mb SAS is not valid?)", th)).either
  }

  override def apply[A](fa: Azure[A]): UIO[A] =
    fa match {
      case StartListing(inPath, secret)         => startListing(inPath, secret)
      case ContinueListing(prevPage)            => UIO { prevPage.next }
      case IsCopied(blob)                       => ???
      case RemoveBlob(blob)                     => UIO { blob.deleteIfExists(); () }
      case SizeOfBlobBytes(blob)                => URIO { blob.downloadAttributes(); blob.getProperties.getLength }
      case StartCopy(src, blob, dst, dstSecret) => ???
    }
}
