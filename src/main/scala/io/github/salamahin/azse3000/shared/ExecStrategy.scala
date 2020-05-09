package io.github.salamahin.azse3000.shared

import cats.data.EitherT
import cats.free.FreeApplicative.FA
import cats.free.{Free, FreeApplicative}
import cats.{Functor, Monad}

object ExecStrategy {
  implicit class LiftSyntax[F[_], A](fa: F[A]) {
    def liftFree: Free[F, A] = Free.liftF(fa)
    def liftFA: FA[F, A]     = FreeApplicative.lift(fa)
  }

  implicit class MonadSyntax[T](value: T) {
    def pureMonad[F[_]: Monad] = Monad[F].pure(value)
  }

  implicit class AsEitherTSyntax[F[_], L, R](either: F[Either[L, R]]) {
    def toEitherT = EitherT(either)
  }

  implicit class LiftToEitherTSyntax[F[_]: Functor, A](fa: F[A]) {
    def toRightEitherT[L] = EitherT.right[L](fa)
  }
}
