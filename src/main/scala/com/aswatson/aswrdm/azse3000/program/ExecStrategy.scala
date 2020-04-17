package com.aswatson.aswrdm.azse3000.program

import cats.free.{Free, FreeApplicative}

class ExecStrategy[F[_], A](fa: F[A]) {
  val seq: Free[F, A]            = Free.liftF(fa)
  val par: FreeApplicative[F, A] = FreeApplicative.lift(fa)
}
