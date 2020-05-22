package io.github.salamahin.azse3000.interpret
import cats.~>
import io.github.salamahin.azse3000.shared.{Delay, DelayCopyStatusCheck}
import zio.clock.Clock
import zio.{URIO, ZIO}

class DelayCompiler extends (Delay ~> URIO[Clock, *]) {
  import zio.duration._

  override def apply[A](fa: Delay[A]) =
    fa match {
      case DelayCopyStatusCheck() => ZIO.sleep(3 second)
    }
}
