package io.github.salamahin.azse3000.expression

import cats.Monad
import io.github.salamahin.azse3000.shared.{Action, And, Expression, ParsedPath}

import scala.annotation.tailrec

trait ActionInterpret2[T] {
  def run(term: Action[ParsedPath]): T
}

object ActionInterpret2 {
  def interpret2[T](int: ActionInterpret2[T])(expression: Expression[ParsedPath]): Vector[T] = {
//    @tailrec
    def iter(expressions: List[Expression[ParsedPath]], acc: Vector[T]): Vector[T] = /*expressions match {
      case Nil                                => acc
      case (head: Action[ParsedPath]) :: tail => iter(tail, acc :+ int.run(head))
      case And(left, right) :: tail           => iter(left :: right :: tail, acc)
    }*/ ???

    iter(expression :: Nil, Vector.empty)
  }
}

trait ActionInterpret[F[_], P, T] {
  def run(term: Action[P]): F[T]
}

object ActionInterpret {
  private class ActionInterpretImpl[F[_]: Monad, P, T] {
    import cats.syntax.either._
    import cats.syntax.functor._

    def run(expression: Expression[P])(int: ActionInterpret[F, P, T]) =
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
    new ActionInterpretImpl[F, P, T].run(expression)(int)

}
