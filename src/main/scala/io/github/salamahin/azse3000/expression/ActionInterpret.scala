package io.github.salamahin.azse3000.expression

import io.github.salamahin.azse3000.shared.{Action, Expression, ParsedPath}

trait ActionInterpret[T] {
  def run(term: Action[ParsedPath]): T
}

object ActionInterpret {
  def interpret[T](int: ActionInterpret[T])(expression: Expression[ParsedPath]): Vector[T] = {
//    @tailrec
    def iter(expressions: List[Expression[ParsedPath]], acc: Vector[T]): Vector[T] = /*expressions match {
      case Nil                                => acc
      case (head: Action[ParsedPath]) :: tail => iter(tail, acc :+ int.run(head))
      case And(left, right) :: tail           => iter(left :: right :: tail, acc)
    }*/ ???

    iter(expression :: Nil, Vector.empty)
  }
}
