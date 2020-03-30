package com.aswatson.aswrdm.azse3000.expression

import cats.Monad
import com.aswatson.aswrdm.azse3000.shared.{Action, And, Expression}

import scala.language.higherKinds

trait ActionInterpret[F[_], T] {
  def run(term: Action): F[T]
}

object ActionInterpret {
  def interpret[F[_]: Monad, T](expression: Expression)(implicit int: ActionInterpret[F, T]) = {
    import cats.syntax.either._
    import cats.syntax.functor._

    Monad[F]
      .tailRecM((expression :: Nil, Vector.empty[T])) {
        case (exps, acc) =>
          exps match {
            case Nil                      => Monad[F].pure(acc.asRight[(List[Expression], Vector[T])])
            case (head: Action) :: tail   => int.run(head).map(x => (tail, acc :+ x).asLeft[Vector[T]])
            case And(left, right) :: tail => Monad[F].pure((left :: right :: tail, acc).asLeft[Vector[T]])
          }
      }
  }
}
