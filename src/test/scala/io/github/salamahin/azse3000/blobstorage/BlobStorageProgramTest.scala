package io.github.salamahin.azse3000.blobstorage
import java.util.concurrent.atomic.AtomicInteger

import io.github.salamahin.azse3000.ParallelInterpreter
import io.github.salamahin.azse3000.ParallelInterpreter._
import io.github.salamahin.azse3000.shared._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import zio._

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

final case class FakeBlobPage(blobs: Vector[FakeBlob], next: Option[FakeBlobPage])

object FakeBlobPage {
  implicit val page = new Page[FakeBlobPage, FakeBlob] {
    override def blobs(page: FakeBlobPage): Vector[FakeBlob] = page.blobs
    override def hasNext(page: FakeBlobPage): Boolean        = page.next.nonEmpty
  }
}

class BlobStorageProgramTest extends AnyFunSuite with Matchers with LogMatchers {
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

      blobOpsInterpreter    = new TestBlobStorageOpsInterpreter(log).withPages(blobs)
      testActionInterpreter = new TestActionInterpreter(log)

      _ <- new BlobStorageProgram[TestAction, FakeBlobPage, FakeBlob]
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

      blobOpsInterpreter    = new TestBlobStorageOpsInterpreter(log)
      testActionInterpreter = new TestActionInterpreter(log)

      _ <- new BlobStorageProgram[TestAction, FakeBlobPage, FakeBlob]
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
