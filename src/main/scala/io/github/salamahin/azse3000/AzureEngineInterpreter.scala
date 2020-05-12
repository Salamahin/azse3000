package io.github.salamahin.azse3000

import java.net.URI

import cats.~>
import com.microsoft.azure.storage.blob.{CloudBlobContainer, CloudBlockBlob, CopyStatus, ListBlobItem}
import com.microsoft.azure.storage.{ResultSegment, StorageCredentialsSharedAccessSignature}
import io.github.salamahin.azse3000.shared._
import zio.{Task, UIO}

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

  private def container(inPath: Path, secret: Secret) =
    new CloudBlobContainer(
      URI.create(s"https://${inPath.account.name}.blob.core.windows.net/${inPath.container.name}"),
      new StorageCredentialsSharedAccessSignature(secret.secret)
    )

  private def blob(dst: Path, secret: Secret) =
    new CloudBlockBlob(
      URI.create(s"https://${dst.account.name}.blob.core.windows.net/${dst.container.name}/${dst.prefix.path}"),
      new StorageCredentialsSharedAccessSignature(secret.secret)
    )

  override def apply[A](fa: Azure[A]) =
    fa match {
      case StartListing(inPath, secret) =>
        Task { container(inPath, secret) }
          .map(c => new ListingPageImpl(c, inPath.prefix, listBlobs(c, inPath.prefix, None)): ListingPage)
          .mapError(th => AzureFailure(s"Failed to list blobs in $inPath", th))
          .either

      case ContinueListing(prevPage) => UIO { prevPage.next }

      case IsCopied(blob) =>
        UIO {
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
          val length   = src.prefix.path.length
          val relative = b.getUri.getRawPath.substring(length + 1)
          val newBlob  = blob(dst.copy(prefix = dst.prefix.copy(s"${dst.prefix.path}/$relative")), dstSecret)

          newBlob.startCopy(b)
          newBlob
        }
          .mapError(th => AzureFailure(s"Failed to initiate copy of ${b.getUri} to $dst", th))
          .either
    }
}
