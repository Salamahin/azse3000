package io.github.salamahin.azse3000.blobstorage
import io.github.salamahin.azse3000.ParallelInterpreter
import io.github.salamahin.azse3000.shared._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import zio._

case class InMemoryBlob(name: String) extends Blob {
  override def isCopied: Either[AzureFailure, Boolean] = Right(true)
  override def toString: String                        = name
}

class MetafilessBlobStorageProgramTest extends AnyFunSuite with Matchers with LogMatchers {
  import cats.syntax.eitherK._
  import zio.interop.catz._

  private val path = Path(
    AccountInfo(StorageAccount("test"), EnvironmentAlias("tst")),
    Container("container"),
    Prefix("prefix"),
    Secret("sas")
  )

  test("Listing of the next page is in parallel with blobs processing") {
    val blobsOnPages = List(
      InMemoryBlob("a") :: InMemoryBlob("b") :: Nil,
      InMemoryBlob("c") :: Nil
    )

    val program = for {
      blobs <- Ref.make[List[List[Blob]]](blobsOnPages)
      log   <- Ref.make[List[String]](Nil)

      blobOpsInterpreter    = new TestBlobStorageOpsInterpreter(blobs, log)
      testActionInterpreter = new TestActionInterpreter(log)

      _ <- new MetafilessBlobStorageProgram[TestAction]
        .listAndProcessBlobs(path)(LogBlobOperation(_).rightc)
        .value
        .foldMap(ParallelInterpreter(blobOpsInterpreter or testActionInterpreter)(ParallelInterpreter.zioApplicative))

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
        "List next batch in container@tst:/prefix start",
      ),
      inAnyOrder(
        "Next batch in container@tst:/prefix listed",
        "Operation on blob a end",
        "Operation on blob b end",
      ),
      inOrder(
        "Operation on blob c start",
        "Operation on blob c end"
      )
    )
  }

}
