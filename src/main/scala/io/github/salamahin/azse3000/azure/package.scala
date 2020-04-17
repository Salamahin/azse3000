package io.github.salamahin.azse3000

import cats.Monad
import cats.data.EitherT
import io.github.salamahin.azse3000.shared.Parallel

package object azure {
  private def sequence[A, B](s: Seq[Either[A, B]]): Either[A, Seq[B]] =
    s.foldRight(Right(Nil): Either[A, List[B]]) { (e, acc) =>
      for (xs <- acc.right; x <- e.right) yield x :: xs
    }

  def eitherTPar[F[_]: Monad](par: Parallel[F]): Parallel[EitherT[F, Throwable, *]] =
    new Parallel[EitherT[F, Throwable, *]] {
      override def traverse[T, U](
        items: Seq[T]
      )(action: T => EitherT[F, Throwable, U]): EitherT[F, Throwable, Seq[U]] = {
        import cats.syntax.functor._

        EitherT {
          par
            .traverse(items)(t => action(t).value)
            .map(x => sequence(x))
        }
      }

      override def zip[T, U](
        first: EitherT[F, Throwable, T],
        second: EitherT[F, Throwable, U]
      ): EitherT[F, Throwable, (T, U)] = {
        import cats.syntax.either._
        import cats.syntax.functor._

        EitherT {
          par
            .zip(first.value, second.value)
            .map {
              case (Right(first), Right(second)) => (first, second).asRight[Throwable]
              case (Left(throwable), _)          => throwable.asLeft[(T, U)]
              case (_, Left(throwable))          => throwable.asLeft[(T, U)]
            }
        }
      }
    }
}
