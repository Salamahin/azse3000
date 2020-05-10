package io.github.salamahin.azse3000.expression

import cats.kernel.Semigroup
import io.github.salamahin.azse3000.shared._

import scala.util.parsing.combinator.{PackratParsers, RegexParsers}

object CommandParser extends RegexParsers with PackratParsers {
  implicit val expSemigroup: Semigroup[Expression] =
    (x: Expression, y: Expression) => And(x, y)

  private def path: Parser[Path] =
    "[\\w@\\-:\\/\\.=]+".r ^^ { x =>
//      Path(x)
      ???
    }

  private def cp: Parser[Expression] =
    ("cp" ~> path ~ rep1(path)) ^^ {
      case p ~ ps =>
        val paths = p +: ps
        val from  = paths.init
        val to    = paths.last

        Copy(from, to)
    }

  private def mv: Parser[Expression] =
    ("mv" ~> path ~ rep1(path)) ^^ {
      case p ~ ps =>
        val paths = p +: ps
        val from  = paths.init
        val to    = paths.last

        Move(from, to)
    }

  private def rm: Parser[Expression] = ("rm" ~> rep1(path)) ^^ (ps => Remove(ps))

  private def count: Parser[Expression] = ("count" ~> rep1(path)) ^^ (ps => Count(ps))

  private lazy val expr: PackratParser[Expression] = (cp | mv | rm | count) ~ rep("&&" ~> expr) ^^ {
    case e1 ~ es =>
      Semigroup
        .combineAllOption(e1 :: es)
        .getOrElse(e1)
  }

  def apply(s: Command): Either[MalformedCommand, Expression] = {
    parseAll(expr, s.cmd) match {
      case Success(result, _) => Right(result)
      case NoSuccess(msg, _)  => Left(MalformedCommand(msg))
    }
  }
}
