package com.aswatson.aswrdm.azse3000.program

import cats.Monad
import cats.data.EitherT

class Program2[F[_]: Monad, B, K](ui: UserInterface[F], fs: FileSystemEngine[F, B, K]) {

  def run() =
    for {
      (expression, secrets) <- EitherT(ui.run())
    } yield ()
}
