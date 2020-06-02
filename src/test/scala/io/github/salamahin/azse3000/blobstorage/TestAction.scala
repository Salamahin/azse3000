package io.github.salamahin.azse3000.blobstorage
import cats.~>
import zio.clock.Clock
import zio.{Ref, URIO, ZIO}

sealed trait TestAction[T]
final case class LogBlobOperation(blob: Blob) extends TestAction[Unit]

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
