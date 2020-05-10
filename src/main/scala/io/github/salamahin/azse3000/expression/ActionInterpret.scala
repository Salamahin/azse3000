package io.github.salamahin.azse3000.expression

import io.github.salamahin.azse3000.shared.{Action, And, Expression}

import scala.annotation.tailrec

trait ActionInterpret[T] {
  def run(term: Action): T
}

object ActionInterpret   {
  def interpret[T](int: ActionInterpret[T])(expression: Expression): Vector[T] = {
    @tailrec
    def iter(expressions: List[Expression], acc: Vector[T]): Vector[T] =
      expressions match {
        case Nil                      => acc
        case (head: Action) :: tail   => iter(tail, acc :+ int.run(head))
        case And(left, right) :: tail => iter(left :: right :: tail, acc)
      }

    iter(expression :: Nil, Vector.empty)
  }
}
