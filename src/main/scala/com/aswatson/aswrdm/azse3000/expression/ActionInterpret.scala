package com.aswatson.aswrdm.azse3000.expression

import cats.Applicative
import com.aswatson.aswrdm.azse3000.shared.{Action, And, Expression}

import scala.annotation.tailrec
import scala.language.higherKinds

trait ActionInterpret[F[_], P, T] {
  def run(term: Action[P]): F[T]
}

object ActionInterpret {
  def interpret[F[_]: Applicative, P, T](
    expression: Expression[P]
  )(implicit int: ActionInterpret[F, P, T]) = {

    @tailrec
    def iter(expressions: List[Expression[P]], acc: Vector[F[T]]): Vector[F[T]] = {
      expressions match {
        case Nil                       => acc
        case (head: Action[P]) :: tail => iter(tail, acc :+ int.run(head))
        case And(left, right) :: tail  => iter(left :: right :: tail, acc)
      }
    }

    import cats.implicits._
    iter(expression :: Nil, Vector.empty).traverse(identity)
  }
}
