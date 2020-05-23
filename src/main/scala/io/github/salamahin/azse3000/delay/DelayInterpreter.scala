package io.github.salamahin.azse3000.delay

import cats.~>
import zio.clock.Clock
import zio.{URIO, ZIO}

class DelayInterpreter extends (DelayOps ~> URIO[Clock, *]) {
  import zio.duration._

  override def apply[A](fa: DelayOps[A]) =
    fa match {
      case DelayCopyStatusCheck() => ZIO.sleep(3 second)
    }
}
