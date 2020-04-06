package com.aswatson.aswrdm.azse3000

import cats.Monad
import cats.data.EitherT
import com.aswatson.aswrdm.azse3000.shared.Parallel

package object azure {
  private def sequence[A, B](s: Seq[Either[A, B]]): Either[A, Seq[B]] =
    s.foldRight(Right(Nil): Either[A, List[B]]) {
      (e, acc) => for (xs <- acc.right; x <- e.right) yield x :: xs
    }


  def par[F[_]: Monad : Parallel]: Parallel[EitherT[F, Throwable, *]] = new Parallel[EitherT[F, Throwable, *]] {
    override def traverse[T, U](items: Seq[T])(action: T => EitherT[F, Throwable, U]): EitherT[F, Throwable, Seq[U]] = {
      import cats.syntax.functor._

      EitherT {
        implicitly[Parallel[F]]
          .traverse(items)(t => action(t).value)
          .map(x => sequence(x))
      }
    }

    override def zip[T, U](first: EitherT[F, Throwable, T], second: EitherT[F, Throwable, U]): EitherT[F, Throwable, (T, U)] = {
      implicitly[Parallel[F]].zip(first, second)
    }
  }
}
