package io.github.salamahin.azse3000.blobstorage

import cats.~>
import com.microsoft.azure.storage.ResultSegment
import com.microsoft.azure.storage.blob.{CloudBlobContainer, CloudBlockBlob, CopyStatus, ListBlobItem}
import io.github.salamahin.azse3000.shared._
import zio.clock.Clock
import zio.{Task, UIO, URIO}

class BlobStorageInterpreter(
  container: Path => CloudBlobContainer,
  blob: Path => CloudBlockBlob,
  maxFetchResults: Int
) extends (BlobStorageOps ~> URIO[Clock, *]) {

  private def listBlobs(cont: CloudBlobContainer, prefix: Prefix, rs: Option[ResultSegment[ListBlobItem]]) =
    cont.listBlobsSegmented(
      prefix.value,
      true,
      null,
      maxFetchResults,
      rs.map(_.getContinuationToken).orNull,
      null,
      null
    )

//  private class ListingPageImpl(cont: CloudBlobContainer, prefix: Prefix, rs: ResultSegment[ListBlobItem]) extends ListingPage {
//    import scala.jdk.CollectionConverters._
//
//    override def blobs: Seq[CloudBlockBlob] =
//      rs.getResults.asScala
//        .map(b => b.asInstanceOf[CloudBlockBlob])
//        .toSeq
//
//    override def next: Option[ListingPage] = {
//      println("list next")
//      if (!rs.getHasMoreResults) None
//      else Some(new ListingPageImpl(cont, prefix, listBlobs(cont, prefix, Some(rs))))
//    }
//  }

  override def apply[A](fa: BlobStorageOps[A]) =
    fa match {
      case StartListing(inPath) =>
//        Task { container(inPath) }
//          .map(c => new ListingPageImpl(c, inPath.prefix, listBlobs(c, inPath.prefix, None)): ListingPage)
//          .mapError(th => AzureFailure(s"Failed to list blobs in $inPath", th))
//          .either
        ???

      case ContinueListing(prevPage) => /*UIO { prevPage.next }*/ ???

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

      case SizeOfBlobBytes(blob) => UIO {
        println("size of blob start")
        Thread.sleep(2000)
        blob.downloadAttributes()
        val l = blob.getProperties.getLength
        println("size of blob finish")
        l
      }

      case StartCopy(src, b, dst) =>
        Task {
          val blobPath      = b.getUri.getPath.drop(src.prefix.value.length)
          val newBlobPrefix = if (dst.prefix.value.nonEmpty) s"${dst.prefix.value}/$blobPath" else blobPath

          val newBlob = blob(dst.copy(prefix = Prefix(newBlobPrefix)))

          newBlob.startCopy(b)
          newBlob
        }
          .mapError(th => AzureFailure(s"Failed to initiate copy of ${b.getUri} to $dst", th))
          .either
    }
}
