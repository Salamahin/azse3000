package io.github.salamahin.azse3000.parsing

import cats.kernel.Semigroup
import cats.~>
import io.github.salamahin.azse3000.shared._
import zio.clock.Clock
import zio.{UIO, URIO}

import scala.util.parsing.combinator.{PackratParsers, RegexParsers}

class ParseCommandInterpreter extends (ParsingOps ~> URIO[Clock, *]) with RegexParsers with PackratParsers {
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
    case acc ~ cont ~ prefix => Path(acc, cont, prefix.getOrElse(Prefix("")), ???)
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

  override def apply[A](fa: ParsingOps[A]) =
    fa match {
      case ParseCommand(cmd) =>
        UIO {
          parseAll(expr, cmd.cmd) match {
            case Success(result, _) => Right(result)
            case NoSuccess(msg, _)  => Left(MalformedCommand(msg))
          }
        }
    }
}
