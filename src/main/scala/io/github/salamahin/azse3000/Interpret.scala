package io.github.salamahin.azse3000

import java.net.URI

import cats.~>
import com.microsoft.azure.storage.blob.{CloudBlobContainer, CloudBlockBlob, ListBlobItem}
import com.microsoft.azure.storage.{ResultSegment, StorageCredentialsSharedAccessSignature}
import io.github.salamahin.azse3000.shared._
import zio.{Task, UIO}

final case class CloudBlob(underlying: CloudBlockBlob) extends Blob

object AzureEngineInterpreter extends (Azure[*, CloudBlob] ~> UIO) {

  class ListingPageImpl(cont: CloudBlobContainer, prefix: Prefix, rs: Option[ResultSegment[ListBlobItem]]) extends ListingPage[CloudBlob] {
    import scala.jdk.CollectionConverters._

    override def blobs: Seq[CloudBlob] =  rs
      .map(x =>
        x.getResults
          .asScala
          .map(b => CloudBlob(b.asInstanceOf[CloudBlockBlob]))
          .toSeq
      )
      .orElse(List.empty[CloudBlob])

    override def next: Option[ListingPage[CloudBlob]] =
      if(!rs.exists(_.getHasMoreResults)) None
      else new ListingPageImpl(cont, prefix, Some(listBlobs))

    private def listBlobs = cont.listBlobsSegmented(
      prefix.path,
      true,
      null,
      5000,
      rs.map(_.getContinuationToken).orNull,
      null,
      null
    )
  }

  private def startListing(inPath: Path, secret: Secret) = {
    val t = Task {
      val c = new CloudBlobContainer(
        URI.create(s"https://${inPath.account}.blob.core.windows.net/${inPath.container}"),
        new StorageCredentialsSharedAccessSignature(secret.secret)
      )

      val rs = c.listBlobsSegmented(
        inPath.prefix.path,
        true,
        null,
        5000,
        null,
        null,
        null
      )

      new ListingPage[CloudBlob] {
        import scala.jdk.CollectionConverters._

        override def blobs: Seq[CloudBlob] = rs
          .getResults
          .asScala
          .map(x => CloudBlob(x.asInstanceOf[CloudBlockBlob]))
          .toSeq

        override def next: Option[ListingPage[CloudBlob]] = {
          if(rs.getHasMoreResults)
        }
      }
    }
  }



  override def apply[A](fa: Azure[A, CloudBlob]): UIO[A] =
    fa match {
      case StartListing(inPath, secret) => startListing(inPath, secret)
      case ContinueListing(prev) => ???
      case IsCopied(blob) => ???
      case RemoveBlob(blob) => ???
      case SizeOfBlobBytes(blob) => ???
      case StartCopy(src, blob, dst, dstSecret) => ???
    }
}
