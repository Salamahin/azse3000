package io.github.salamahin.azse3000.blobstorage
import java.util.concurrent.atomic.AtomicInteger

import io.github.salamahin.azse3000.blobstorage.BlobStorageAuxProgramTest._
import io.github.salamahin.azse3000.shared._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import zio._
import zio.clock.Clock

object BlobStorageAuxProgramTest {
  import zio.duration._

  final case class FakeBlobPage(blobs: Vector[FakeBlob], next: Option[FakeBlobPage])

  object FakeBlobPage {
    implicit val page = new Page[FakeBlobPage, FakeBlob] {
      override def blobs(page: FakeBlobPage): Vector[FakeBlob] = page.blobs
      override def hasNext(page: FakeBlobPage): Boolean        = page.next.nonEmpty
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

  class LogActionInterpreter(log: Ref[List[String]]) {
    def logBlobOperation(blob: FakeBlob) =
      for {
        _ <- log.update(_ :+ s"Operation on blob $blob start")
        _ <- ZIO.sleep(500 millis)
        _ <- log.update(_ :+ s"Operation on blob $blob end")
      } yield ()
  }

  class BlobStorageAuxOpsInterpreter(log: Ref[List[String]]) extends BlobStorageAux[URIO[Clock, *], FakeBlobPage, FakeBlob] {
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

    override def listPage(inPath: Path, prev: Option[FakeBlobPage]): URIO[Clock, Either[AzureFailure, FakeBlobPage]] =
      for {
        _    <- log.update(_ :+ s"List next batch in $inPath start")
        _    <- ZIO.sleep(200 millis)
        page <- nextPage
        _    <- log.update(_ :+ s"Next batch in $inPath listed")
      } yield Right[AzureFailure, FakeBlobPage](page.get)

    override def downloadAttributes(blob: FakeBlob): URIO[Clock, Either[AzureFailure, FakeBlob]] =
      for {
        _ <- log.update(_ :+ s"Downloading attributes of a blob $blob")
      } yield Right[AzureFailure, FakeBlob](blob)

    override def waitForCopyStateUpdate(): URIO[Clock, Unit] =
      for {
        _ <- log.update(_ :+ "Waiting a bit for another blob status check attempt")
        _ <- ZIO.sleep(200 millis)
      } yield ()
  }
}

class BlobStorageAuxProgramTest extends AnyFunSuite with Matchers with LogMatchers {
  import FakeBlob._
  import FakeBlobPage._
  import zio.interop.catz.core._

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

      auxInterpreter       = new BlobStorageAuxOpsInterpreter(log).withPages(blobs)
      logActionInterpreter = new LogActionInterpreter(log)

      _ <- new BlobStorageAuxProgram[URIO[Clock, *], FakeBlobPage, FakeBlob](auxInterpreter)
        .listAndProcessBlobs(path)(logActionInterpreter.logBlobOperation)
        .value

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

      auxInterpreter = new BlobStorageAuxOpsInterpreter(log)

      _ <- new BlobStorageAuxProgram[URIO[Clock, *], FakeBlobPage, FakeBlob](auxInterpreter)
        .waitUntilBlobsCopied(blobsOnPages)

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
