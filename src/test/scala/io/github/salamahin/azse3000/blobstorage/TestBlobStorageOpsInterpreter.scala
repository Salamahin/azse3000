package io.github.salamahin.azse3000.blobstorage
import cats.~>
import io.github.salamahin.azse3000.shared.AzureFailure
import zio.clock.Clock
import zio.{Ref, URIO, ZIO}

class TestBlobStorageOpsInterpreter(log: Ref[List[String]]) extends (BlobStorageOps ~> URIO[Clock, *]) {
  import zio.duration._

  private var _pages: Ref[List[List[Blob]]] = _

  def withPages(pages: Ref[List[List[Blob]]]) = {
    _pages = pages
    this
  }

  private def nextPage =
    for {
      remainedPages <- _pages.get
      _             <- _pages.set(remainedPages.tail)
    } yield new BlobsPage {
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
        } yield Right[AzureFailure, BlobsPage](page)

      case DownloadAttributes(blob) =>
        for {
          _ <- log.update(_ :+ s"Downloading attributes of a blob $blob")
        } yield Right[AzureFailure, Blob](blob)

      case WaitForCopyStateUpdate() =>
        for {
          _ <- log.update(_ :+ "Waiting a bit for another blob status check attempt")
          _ <- ZIO.sleep(200 millis)
        } yield ()

      case StartCopying(src, dst) => ???
    }
}
