package io.github.salamahin.azse3000.azure

import cats.Monad
import io.github.salamahin.azse3000.shared.Parallel

class Continuable[F[_]: Monad](par: Parallel[F]) {

  def doAndContinue[C, T](
    init: () => F[C],
    next: C => F[Option[C]],
    map: C => F[T]
  ): F[Seq[T]] = {
    import cats.syntax.either._
    import cats.syntax.flatMap._
    import cats.syntax.functor._

    init()
      .flatMap { ct =>
        Monad[F].tailRecM((Some(ct): Option[C], Vector.empty[T])) {
          case (current, acc) =>
            current match {
              case None => Monad[F].pure(acc.asRight[(Option[C], Vector[T])])

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
