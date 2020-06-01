package io.github.salamahin.azse3000.blobstorage
import cats.~>
import io.github.salamahin.azse3000.shared.AzureFailure
import zio.{Ref, UIO}

class TestBlobStorageOpsInterpreter(pages: List[List[Blob]]) extends (BlobStorageOps ~> UIO) {
  private val _pages = Ref.make(pages)

  private def nextPage =
    for {
      pages         <- _pages
      remainedPages <- pages.get
      _             <- pages.set(remainedPages.tail)
    } yield new BlobsPage2 {
      override def blobs: Seq[Blob] = remainedPages.head
      override def hasNext: Boolean = remainedPages.size > 1
    }

  override def apply[A](fa: BlobStorageOps[A]): UIO[A] =
    fa match {
      case ListPage(inPath, _) =>
        for {
          _    <- UIO(println(s"List in $inPath"))
          page <- nextPage
        } yield Right[AzureFailure, BlobsPage2](page)

      case DownloadAttributes(blob) => ???
      case WaitForCopyStateUpdate() => ???
    }
}
