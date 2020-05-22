package io.github.salamahin.azse3000.expression

import cats.kernel.Semigroup
import io.github.salamahin.azse3000.shared._

import scala.util.parsing.combinator.{PackratParsers, RegexParsers}

object CommandParser extends RegexParsers with PackratParsers {
  implicit val expSemigroup: Semigroup[Expression] =
    (x: Expression, y: Expression) => And(x, y)

  private def commandWithFrom[T](cmd: String, instance: Seq[Path] => T) = cmd ~> rep1(path) ^^ instance
  private def commandWithFromAndTo[T](cmd: String, instance: (Seq[Path], Path) => T) =
    cmd ~> path ~ rep1(path) ^^ {
      case p ~ ps =>
        val paths = p +: ps
        val from  = paths.init
        val to    = paths.last
        instance(from, to)
    }

  private val account   = "[\\w-]+".r ^^ Account
  private val container = "[\\w-]+".r ^^ Container
  private val prefix    = "[\\w-@/=.]+".r ^^ Prefix

  private val path = ((account <~ "@") ~ (container <~ ":/") ~ (prefix ?)) ^^ {
    case acc ~ cont ~ prefix => Path(acc, cont, prefix)
  }

  private val cp = commandWithFromAndTo("cp", (from, to) => Copy(from, to))
  private val mv = commandWithFromAndTo("mv", (from, to) => Move(from, to))

  private val rm    = commandWithFrom("rm", paths => Remove(paths))
  private val count = commandWithFrom("count", paths => Count(paths))
  private val size  = commandWithFrom("size", paths => Size(paths))

  private lazy val expr: PackratParser[Expression] = (cp | mv | rm | count | size) ~ rep("&&" ~> expr) ^^ {
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
