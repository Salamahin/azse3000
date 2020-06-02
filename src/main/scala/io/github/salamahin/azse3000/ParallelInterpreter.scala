package io.github.salamahin.azse3000
import cats.free.FreeApplicative
import cats.{Applicative, ~>}
import zio.clock.Clock
import zio.{UIO, URIO, ZIO}

final case class ParallelInterpreter[F[_], G[_]](f: F ~> G)(implicit ev: Applicative[G]) extends (FreeApplicative[F, *] ~> G) {
  override def apply[A](fa: FreeApplicative[F, A]): G[A] = fa.foldMap(f)
}

object ParallelInterpreter {
  implicit val zioApplicative = new Applicative[URIO[Clock, *]] {
    override def pure[A](x: A): URIO[Clock, A]                         = ZIO.succeed(x)
    override def ap[A, B](ff: URIO[Clock, A => B])(fa: URIO[Clock, A]): URIO[Clock, B] = apply2(ff, fa)(_(_))

    def apply2[A, B, C](a: => URIO[Clock, A], b: => URIO[Clock, B])(f: (A, B) => C): URIO[Clock, C] = {
      (a zipWithPar b)(f)
    }
  }
}
