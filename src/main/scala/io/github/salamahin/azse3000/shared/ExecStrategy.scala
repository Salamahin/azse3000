package io.github.salamahin.azse3000.shared

import cats.arrow.FunctionK
import cats.free.FreeApplicative.FA
import cats.free.{Free, FreeApplicative}

final case class ExecStrategy[F[_], A](fa: F[A]) {
  val seq: Free[F, A] = Free.liftF(fa)
  val par: FA[F, A]   = FreeApplicative.lift(fa)
}

object ExecStrategy {
  type Program[F[_], A] = Free[FA[F, *], A]

  implicit class FreeApplicativeOps[F[_], A](fa: FA[F, A]) {
    def asProgramStep: Program[F, A] = Free.liftF[FA[F, *], A](fa)
  }

  implicit class FreeOps[F[_], A](free: Free[F, A]) {
    def asProgramStep: Program[F, A] = {
      free.foldMap[Program[F, *]](new FunctionK[F, Program[F, *]] {
        override def apply[S](fa: F[S]): Program[F, S] = Free.liftF(FreeApplicative.lift(fa))
      })
    }
  }
}
