package com.aswatson.aswrdm.azse3000.expression

import cats.kernel.Semigroup
import com.aswatson.aswrdm.azse3000.shared._

import scala.util.parsing.combinator.{PackratParsers, RegexParsers}

object CommandParser extends RegexParsers with PackratParsers {
  implicit val expSemigroup: Semigroup[Expression[Path]] =
    (x: Expression[Path], y: Expression[Path]) => And(x, y)

  private def path: Parser[Path] =
    "[\\w@\\-:/.]+".r ^^ { x =>
      Path(x)
    }

  private def cp: Parser[Expression[Path]] = ("cp" ~> path ~ rep1(path)) ^^ {
    case p ~ ps =>
      val paths = p +: ps
      val from  = paths.init
      val to    = paths.last

      Copy(from, to)
  }

  private def mv: Parser[Expression[Path]] = ("mv" ~> path ~ rep1(path)) ^^ {
    case p ~ ps =>
      val paths = p +: ps
      val from  = paths.init
      val to    = paths.last

      Move(from, to)
  }

  private def rm: Parser[Expression[Path]] = ("rm" ~> rep1(path)) ^^ (ps => Remove(ps))

  private def count: Parser[Expression[Path]] = ("count" ~> rep1(path)) ^^ (ps => Count(ps))

  private lazy val expr: PackratParser[Expression[Path]] = (cp | mv | rm | count) ~ rep("&&" ~> expr) ^^ {
    case e1 ~ es =>
      Semigroup
        .combineAllOption(e1 :: es)
        .getOrElse(e1)
  }

  def apply(s: Command): Either[InvalidCommand, Expression[Path]] = {
    parseAll(expr, s.cmd) match {
      case Success(result, _) => Right(result)
      case NoSuccess(msg, _)  => Left(InvalidCommand(msg))
    }
  }
}
