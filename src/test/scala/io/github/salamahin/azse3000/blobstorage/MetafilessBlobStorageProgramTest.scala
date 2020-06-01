package io.github.salamahin.azse3000.blobstorage
import cats.~>
import io.github.salamahin.azse3000.ParallelInterpreter
import io.github.salamahin.azse3000.shared._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import zio.{DefaultRuntime, UIO}

case class InMemoryBlob(name: String) extends Blob {
  override def isCopied: Either[AzureFailure, Boolean] = Right(true)
}

class MetafilessBlobStorageProgramTest extends AnyFunSuite with Matchers {
  private val path = Path(
    AccountInfo(StorageAccount("test"), EnvironmentAlias("tst")),
    Container("container"),
    Prefix("prefix"),
    Secret("sas")
  )

  class TestActionInterpreter extends (TestAction ~> UIO) {
    override def apply[A](fa: TestAction[A]): UIO[A] =
      fa match {
        case LogBlobOperation(blob) => UIO(println(s"New operation on blob $blob"))
      }
  }

  test("aaaaaa") {
    import cats.syntax.eitherK._
    import zio.interop.catz._

    val blobOpsInterpreter = new TestBlobStorageOpsInterpreter(
      List(
        InMemoryBlob("a") :: InMemoryBlob("b") :: Nil,
        InMemoryBlob("c") :: Nil
      )
    )

    val program = new MetafilessBlobStorageProgram[TestAction]
      .listAndProcessBlobs(path)(LogBlobOperation(_).rightc)
      .value
      .foldMap(ParallelInterpreter(blobOpsInterpreter or new TestActionInterpreter)(ParallelInterpreter.uioApplicative))

    new DefaultRuntime {}.unsafeRunSync(program)
  }

}
