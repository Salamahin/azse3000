package io.github.salamahin.azse3000.blobstorage
import cats.~>
import io.github.salamahin.azse3000.shared.AzureFailure
import zio.clock.Clock
import zio.{Ref, UIO, URIO, ZIO}

class TestBlobStorageOpsInterpreter(pages: Ref[List[List[Blob]]], log: Ref[List[String]]) extends (BlobStorageOps ~> URIO[Clock, *]) {
  import zio.duration._

  private def nextPage =
    for {
      remainedPages <- pages.get
      _             <- pages.set(remainedPages.tail)
    } yield new BlobsPage2 {
      override def blobs: Seq[Blob] = remainedPages.head
      override def hasNext: Boolean = remainedPages.size > 1
    }

  override def apply[A](fa: BlobStorageOps[A]): URIO[Clock, A] =
    fa match {
      case ListPage(inPath, _) =>
        for {
          _    <- log.update(_ :+ s"List next batch in $inPath start")
          _    <- ZIO.sleep(200 millis)
          page <- nextPage
          _    <- log.update(_ :+ s"Next batch in $inPath listed")
        } yield Right[AzureFailure, BlobsPage2](page)

      case DownloadAttributes(blob) => ???
      case WaitForCopyStateUpdate() => ???
    }

  "asdasda".takeRight(4)
}
