package com.aswatson.aswrdm.azse3000.expression

import cats.Monad
import com.aswatson.aswrdm.azse3000.shared.{Action, And, Expression}

import scala.language.higherKinds

trait ActionInterpret[F[_], P, T] {
  def run(term: Action[P]): F[T]
}

object ActionInterpret {
  private class ActionInterpretImpl[F[_]: Monad, P, T] {
    import cats.syntax.either._
    import cats.syntax.functor._

    def run(expression: Expression[P])(implicit int: ActionInterpret[F, P, T]) =
      Monad[F]
        .tailRecM((expression :: Nil, Vector.empty[T])) {
          case (exps, acc) =>
            exps match {
              case Nil                       => Monad[F].pure(acc.asRight[(List[Expression[P]], Vector[T])])
              case (head: Action[P]) :: tail => int.run(head).map(x => (tail, acc :+ x).asLeft[Vector[T]])
              case And(left, right) :: tail  => Monad[F].pure((left :: right :: tail, acc).asLeft[Vector[T]])
            }
        }
  }

  def interpret[F[_]: Monad, P, T](expression: Expression[P])(implicit int: ActionInterpret[F, P, T]): F[Vector[T]] =
    new ActionInterpretImpl[F, P, T].run(expression)
}
