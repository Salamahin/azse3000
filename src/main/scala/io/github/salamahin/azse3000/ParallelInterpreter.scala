package io.github.salamahin.azse3000
import cats.free.FreeApplicative
import cats.{Applicative, ~>}
import zio.UIO

final case class ParallelInterpreter[F[_], G[_]](f: F ~> G)(implicit ev: Applicative[G]) extends (FreeApplicative[F, *] ~> G) {
  override def apply[A](fa: FreeApplicative[F, A]): G[A] = fa.foldMap(f)
}

object ParallelInterpreter {
  implicit val uioApplicative = new Applicative[UIO] {
    override def pure[A](x: A): UIO[A]                         = UIO(x)
    override def ap[A, B](ff: UIO[A => B])(fa: UIO[A]): UIO[B] = apply2(ff, fa)(_(_))

    def apply2[A, B, C](a: => UIO[A], b: => UIO[B])(f: (A, B) => C): UIO[C] = {
      (a zipWithPar b)(f)
    }
  }
}
