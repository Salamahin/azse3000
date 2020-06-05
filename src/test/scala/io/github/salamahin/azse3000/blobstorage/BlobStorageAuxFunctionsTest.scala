package io.github.salamahin.azse3000.blobstorage
import java.util.concurrent.atomic.AtomicInteger

import cats.~>
import io.github.salamahin.azse3000.ParallelInterpreter
import io.github.salamahin.azse3000.ParallelInterpreter._
import io.github.salamahin.azse3000.blobstorage.BlobStorageAuxFunctionsTest._
import io.github.salamahin.azse3000.shared._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import zio._
import zio.clock.Clock

object BlobStorageAuxFunctionsTest {
  sealed trait TestAction[T]
  final case class LogBlobOperation(blob: FakeBlob) extends TestAction[Unit]

  final case class FakeBlobPage(blobs: Vector[FakeBlob], next: Option[FakeBlobPage])

  object FakeBlobPage {
    implicit val page = new Page[FakeBlobPage, FakeBlob] {
      override def blobs(page: FakeBlobPage): Vector[FakeBlob] = page.blobs
      override def hasNext(page: FakeBlobPage): Boolean        = page.next.nonEmpty
    }
  }


  class TestActionInterpreter(log: Ref[List[String]]) extends (TestAction ~> URIO[Clock, *]) {
    import zio.duration._

    override def apply[A](fa: TestAction[A]): URIO[Clock, A] =
      fa match {
        case LogBlobOperation(blob) =>
          for {
            _ <- log.update(_ :+ s"Operation on blob $blob start")
            _ <- ZIO.sleep(500 millis)
            _ <- log.update(_ :+ s"Operation on blob $blob end")
          } yield ()
      }
  }

  class BlobStorageAuxOpsInterpreter(log: Ref[List[String]]) extends (BlobStorageAuxOps[*, FakeBlobPage, FakeBlob] ~> URIO[Clock, *]) {
    import zio.duration._

    private var _pages: Ref[Option[FakeBlobPage]] = _

    def withPages(pages: Ref[Option[FakeBlobPage]]) = {
      _pages = pages
      this
    }

    private def nextPage =
      for {
        page <- _pages.get
        _    <- _pages.update(_ => page.flatMap(_.next))
      } yield page

    override def apply[A](fa: BlobStorageAuxOps[A, FakeBlobPage, FakeBlob]): URIO[Clock, A] =
      fa match {
        case ListPage(inPath, _) =>
          for {
            _    <- log.update(_ :+ s"List next batch in $inPath start")
            _    <- ZIO.sleep(200 millis)
            page <- nextPage
            _    <- log.update(_ :+ s"Next batch in $inPath listed")
          } yield Right[AzureFailure, FakeBlobPage](page.get)

        case DownloadAttributes(blob) =>
          for {
            _ <- log.update(_ :+ s"Downloading attributes of a blob $blob")
          } yield Right[AzureFailure, FakeBlob](blob)

        case WaitForCopyStateUpdate() =>
          for {
            _ <- log.update(_ :+ "Waiting a bit for another blob status check attempt")
            _ <- ZIO.sleep(200 millis)
          } yield ()
      }
  }

  final case class FakeBlob(name: String, private val checksUntilCopied: Int = 1) {
    private val checksRemained = new AtomicInteger(checksUntilCopied)

    def isCopied: Either[AzureFailure, Boolean] = Right(checksRemained.updateAndGet(_ - 1) == 0)
    override def toString: String               = name
  }

  object FakeBlob {
    implicit val blob = new Blob[FakeBlob] {
      override def isCopied(blob: FakeBlob): Either[AzureFailure, Boolean] = blob.isCopied
    }
  }
}

class BlobStorageAuxFunctionsTest extends AnyFunSuite with Matchers with LogMatchers {
  import FakeBlob._
  import FakeBlobPage._
  import cats.syntax.eitherK._
  import zio.interop.catz._

  private val path = Path(
    AccountInfo(StorageAccount("test"), EnvironmentAlias("tst")),
    Container("container"),
    Prefix("prefix"),
    Secret("sas")
  )

  test("listing of the next page is in parallel with blobs processing") {
    val blobsOnPages = FakeBlobPage(
      Vector(FakeBlob("a"), FakeBlob("b")),
      Some(
        FakeBlobPage(Vector(FakeBlob("c")), None)
      )
    )

    val program = for {
      blobs <- Ref.make(Some(blobsOnPages): Option[FakeBlobPage])
      log   <- Ref.make[List[String]](Nil)

      blobOpsInterpreter    = new BlobStorageAuxOpsInterpreter(log).withPages(blobs)
      testActionInterpreter = new TestActionInterpreter(log)

      _ <- new BlobStorageAuxFunctions[TestAction, FakeBlobPage, FakeBlob]
        .listAndProcessBlobs(path)(LogBlobOperation(_).rightc)
        .value
        .foldMap(ParallelInterpreter(blobOpsInterpreter or testActionInterpreter)(zioApplicative))

      logged <- log.get
    } yield logged

    val log = new DefaultRuntime {}.unsafeRun(program)
    log should containMessages(
      inOrder(
        "List next batch in container@tst:/prefix start",
        "Next batch in container@tst:/prefix listed"
      ),
      inAnyOrder(
        "Operation on blob b start",
        "Operation on blob a start",
        "List next batch in container@tst:/prefix start"
      ),
      inAnyOrder(
        "Next batch in container@tst:/prefix listed",
        "Operation on blob a end",
        "Operation on blob b end"
      ),
      inOrder(
        "Operation on blob c start",
        "Operation on blob c end"
      )
    )
  }

  test("download blob attributes in parallel with small delay until all blobs copied") {
    val blobsOnPages = Vector(FakeBlob("a", 1), FakeBlob("b", 2), FakeBlob("c", 3))

    val program = for {
      log <- Ref.make[List[String]](Nil)

      blobOpsInterpreter    = new BlobStorageAuxOpsInterpreter(log)
      testActionInterpreter = new TestActionInterpreter(log)

      _ <- new BlobStorageAuxFunctions[TestAction, FakeBlobPage, FakeBlob]
        .waitUntilBlobsCopied(blobsOnPages)
        .foldMap(ParallelInterpreter(blobOpsInterpreter or testActionInterpreter)(zioApplicative))

      logged <- log.get
    } yield logged

    val log = new DefaultRuntime {}.unsafeRun(program)
    log should containMessages(
      inAnyOrder(
        "Downloading attributes of a blob a",
        "Downloading attributes of a blob b",
        "Downloading attributes of a blob c"
      ),
      inOrder(
        "Waiting a bit for another blob status check attempt"
      ),
      inAnyOrder(
        "Downloading attributes of a blob b",
        "Downloading attributes of a blob c"
      ),
      inOrder(
        "Waiting a bit for another blob status check attempt",
        "Downloading attributes of a blob c"
      )
    )
  }
}
