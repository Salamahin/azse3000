package io.github.salamahin.azse3000
import cats.InjectK

package object delay {
  sealed trait DelayOps[T]
  final case class DelayCopyStatusCheck() extends DelayOps[Unit]

  final class Delays[F[_]](implicit inj: InjectK[DelayOps, F]) {
    def delayCopyStatusCheck() = inj(DelayCopyStatusCheck())
  }

  object Delays {
    implicit def delays[F[_]](implicit I: InjectK[DelayOps, F]): Delays[F] = new Delays[F]
  }
}
