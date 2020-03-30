package com.aswatson.aswrdm.azse3000.program

import cats.Monad
import com.aswatson.aswrdm.azse3000.shared.Parallel

trait Continuable[F[_]] {

  def doAnd[C, T](init: () => F[C], next: C => F[Option[C]], map: C => F[T])(
    implicit monad: Monad[F],
    par: Parallel[F]
  ): F[Seq[T]] = {
    import cats.syntax.flatMap._
    import cats.syntax.either._
    import cats.syntax.functor._

    init()
      .flatMap { ct =>
        monad.tailRecM((Some(ct): Option[C], Vector.empty[T])) {
          case (current, acc) =>
            current match {
              case None => monad.pure(acc.asRight[(Option[C], Vector[T])])

              case Some(current) =>
                par
                  .zip(map(current), next(current))
                  .map {
                    case (currentMapped, nextBatch) => (nextBatch, acc :+ currentMapped).asLeft[Vector[T]]
                  }
            }
        }
      }
  }
}
