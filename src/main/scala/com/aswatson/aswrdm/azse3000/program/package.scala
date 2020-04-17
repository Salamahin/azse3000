package com.aswatson.aswrdm.azse3000

import cats.arrow.FunctionK
import cats.free.{Free, FreeApplicative}

package object program {
  type Program[F[_], A] = Free[FreeApplicative[F, *], A]

  implicit class FreeApSyntax[F[_], A](freeap: FreeApplicative[F, A]) {
    def asProgramStep: Program[F, A] = Free.liftF(freeap)
  }

  implicit class FreeSyntax[F[_], A](free: Free[F, A]) {
    def asProgramStep: Program[F, A] = {
      free.foldMap(new FunctionK[F, Program[F, *]] {
        override def apply[U](fa: F[U]): Program[F, U] = Free.liftF(FreeApplicative.lift(fa))
      })
    }
  }
}
