package com.aswatson.aswrdm.azse3000.azure

import com.aswatson.aswrdm.azse3000.shared._
import com.microsoft.azure.storage.blob.{CloudBlobContainer, CloudBlockBlob, ListBlobItem}
import com.microsoft.azure.storage.{ResultContinuation, ResultSegment}
import zio.{RIO, Task, ZIO}

class ZioAzureFileSystem[E](batchSize: Int) extends FileSystem[RIO[E, *], CloudBlockBlob, CloudBlobContainer] {
  override def foreachFile[U](cont: CloudBlobContainer, prefix: Path)(
    batchOperation: Seq[CloudBlockBlob] => RIO[E, Seq[U]]
  ) = {

    def listBatch(token: ResultContinuation) = Task {
      cont.listBlobsSegmented(
        prefix.path,
        true,
        null,
        batchSize,
        token,
        null,
        null
      )
    }

    def batchBlobsAndContinue(segment: ResultSegment[ListBlobItem]): RIO[E, Seq[U]] = {
      import scala.collection.JavaConverters._

      val listedItems = segment
        .getResults
        .asScala
        .map(_.asInstanceOf[CloudBlockBlob])
        .toVector

      val processedItems = batchOperation(listedItems)

      if (segment.getHasMoreResults) for {
        (previouslyProcessedItems, nextBatch) <- processedItems <&> listBatch(segment.getContinuationToken)
        nextProcessedItems                    <- batchBlobsAndContinue(nextBatch)
      } yield previouslyProcessedItems ++ nextProcessedItems
      else processedItems
    }

    val processing = for {
      firstBatch     <- listBatch(null)
      processedBlobs <- batchBlobsAndContinue(firstBatch)
    } yield processedBlobs

    processing
      .mapError(e => FileSystemFailure(Path(cont.getUri.toString), e))
      .either
  }

  override def copyContent(file: CloudBlockBlob, to: CloudBlockBlob): Task[Either[FileOperationFailed, Unit]] = {
    import cats.syntax.either._

    val copy = ZIO { to.startCopy(file, null, true, null, null, null, null) }

    copy.fold(
      cause => FileOperationFailed(Path(file.getUri.toString), cause).asLeft[Unit],
      _ => ().asRight[FileOperationFailed]
    )
  }

  override def remove(file: CloudBlockBlob): Task[Either[FileOperationFailed, Unit]] = {
    import cats.syntax.either._

    val delete = Task(file.delete())

    delete.fold(
      cause => FileOperationFailed(Path(file.getUri.toString), cause).asLeft[Unit],
      _ => ().asRight[FileOperationFailed]
    )
  }
}
