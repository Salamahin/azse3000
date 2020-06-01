package io.github.salamahin.azse3000.blobstorage
import cats.~>
import io.github.salamahin.azse3000.shared._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import zio.{Ref, UIO}

class MetafilessBlobStorageProgramTest extends AnyFunSuite with Matchers {

  val actionLog = Ref.make(Vector.empty[String])

  case class InMemoryBlob(name: String) extends Blob {
    override def isCopied: Either[AzureFailure, Boolean] = Right(true)
  }

  class TestInterpreter(pages: List[List[Blob]] = Nil) extends (MetafilelessOps ~> UIO) {
    def addBlobOnPage(blobs: List[Blob]) = new TestInterpreter(pages :+ blobs)

    private def remainedPages = Ref.make(pages)

    private def nextPage =
      for {
        pages    <- remainedPages
        allPages <- pages.get
        _        <- pages.set(allPages.tail)
      } yield new BlobsPage2 {
        override def blobs: Seq[Blob] = allPages.head
        override def hasNext: Boolean = allPages.size > 1
      }

    private def log(msg: String) =
      for {
        log <- actionLog
        _   <- log.update(_ :+ msg)
      } yield ()

    override def apply[A](fa: MetafilelessOps[A]): UIO[A] =
      fa match {
        case ListPage(inPath, _) =>
          for {
            _    <- log(s"List in $inPath")
            page <- nextPage
          } yield Right[AzureFailure, BlobsPage2](page)

        case DownloadAttributes(blob) => ???
        case WaitForCopyStateUpdate() => ???
      }
  }

  val path = Path(
    AccountInfo(StorageAccount("test"), EnvironmentAlias("tst")),
    Container("container"),
    Prefix("prefix"),
    Secret("sas")
  )

  test("") {
    import cats.implicits._

    val testee = new TestInterpreter()
      .addBlobOnPage(InMemoryBlob("a") :: InMemoryBlob("b") :: Nil)
      .addBlobOnPage(InMemoryBlob("c") :: Nil)

    val a = new MetafilessBlobStorageProgram()
      .listAndProcessBlobs(path)()



  }

}
