package io.github.salamahin.azse3000.interpret

import cats.~>
import com.microsoft.azure.storage.ResultSegment
import com.microsoft.azure.storage.blob.{CloudBlobContainer, CloudBlockBlob, CopyStatus, ListBlobItem}
import io.github.salamahin.azse3000.shared._
import zio.clock.Clock
import zio.{Task, UIO, URIO}

class BlobStorageAPICompiler(
  container: (Path, Secret) => CloudBlobContainer,
  blob: (Path, Secret) => CloudBlockBlob,
  maxFetchResults: Int
) extends (BlobStorageAPI ~> URIO[Clock, *]) {

  private def listBlobs(cont: CloudBlobContainer, prefix: Prefix, rs: Option[ResultSegment[ListBlobItem]]) =
    cont.listBlobsSegmented(
      prefix.path,
      true,
      null,
      maxFetchResults,
      rs.map(_.getContinuationToken).orNull,
      null,
      null
    )

  private class ListingPageImpl(cont: CloudBlobContainer, prefix: Prefix, rs: ResultSegment[ListBlobItem]) extends ListingPage {
    import scala.jdk.CollectionConverters._

    override def blobs: Seq[CloudBlockBlob] =
      rs.getResults.asScala
        .map(b => b.asInstanceOf[CloudBlockBlob])
        .toSeq

    override def next: Option[ListingPage] =
      if (!rs.getHasMoreResults) None
      else Some(new ListingPageImpl(cont, prefix, listBlobs(cont, prefix, Some(rs))))
  }

  override def apply[A](fa: BlobStorageAPI[A]) =
    fa match {
      case StartListing(inPath, secret) =>
        Task { container(inPath, secret) }
          .map(c => new ListingPageImpl(c, inPath.prefix, listBlobs(c, inPath.prefix, None)): ListingPage)
          .mapError(th => AzureFailure(s"Failed to list blobs in $inPath", th))
          .either

      case ContinueListing(prevPage) => UIO { prevPage.next }

      case IsCopied(blob) =>
        UIO {
          blob.downloadAttributes()

          import cats.syntax.either._
          val cs = blob.getCopyState
          if (cs.getStatus == CopyStatus.SUCCESS) true.asRight
          else if (cs.getStatus == CopyStatus.PENDING) false.asRight
          else
            AzureFailure(
              s"Unexpected copy status of ${blob.getUri}: ${cs.getStatus}",
              new IllegalStateException(cs.getStatusDescription)
            ).asLeft
        }

      case RemoveBlob(blob) =>
        Task { blob.deleteIfExists(); () }
          .mapError(th => AzureFailure(s"Failed to remove ${blob.getUri}", th))
          .either

      case SizeOfBlobBytes(blob) => UIO { blob.downloadAttributes(); blob.getProperties.getLength }

      case StartCopy(src, b, dst, dstSecret) =>
        Task {
          val blobPath      = b.getUri.getPath.drop(src.prefix.path.length)
          val newBlobPrefix = if (dst.prefix.path.nonEmpty) s"${dst.prefix.path}/$blobPath" else blobPath

          val newBlob = blob(dst.copy(prefix = Prefix(newBlobPrefix)), dstSecret)

          newBlob.startCopy(b)
          newBlob
        }
          .mapError(th => AzureFailure(s"Failed to initiate copy of ${b.getUri} to $dst", th))
          .either
    }
}
