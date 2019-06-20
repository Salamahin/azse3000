package com.aswatson.aswrdm.azse3000.expression

import cats.Id
import cats.kernel.{Monoid, Semigroup}
import com.aswatson.aswrdm.azse3000.shared._
import org.scalatest.{FunSuite, Matchers}

class InputParserTest extends FunSuite with Matchers {
  import InputParserTest._

  private val primitives = Seq(
    Command("cp a1 a2 a3 b") ->
      And(
        And(
          Copy(Path("a1"), Path("b")),
          Copy(Path("a2"), Path("b"))
        ),
        Copy(Path("a3"), Path("b"))
      ),
    Command("mv a1 a2 a3 b") ->
      And(
        And(
          Move(Path("a1"), Path("b")),
          Move(Path("a2"), Path("b"))
        ),
        Move(Path("a3"), Path("b"))
      ),
    Command("rm a1 a2 a3") ->
      And(
        And(
          Remove(Path("a1")),
          Remove(Path("a2"))
        ),
        Remove(Path("a3"))
      )
  )

  primitives.foreach {
    case (expression, expectedTree) =>
      test(s"should parse primitive `$expression`") {
        InputParser(expression) match {
          case Right(c) => c should be(expectedTree)
          case Left(v)  => fail(s"Failed to parse tree: $v")
        }
      }
  }

  test("should parse `and` expression") {
    val expr = Semigroup.combineAllOption(primitives.map(_._1))

    InputParser(expr.get).map(x => ActionInterpret.interpret(x)) match {
      case Right(c: Vector[String]) =>
        c should contain inOrderOnly (
          "cp a1 to b",
          "cp a2 to b",
          "cp a3 to b",
          "mv a1 to b",
          "mv a2 to b",
          "mv a3 to b",
          "rm a1",
          "rm a2",
          "rm a3"
        )
      case Left(v) => fail(s"Failed to parse tree: $v")
    }
  }
}

object InputParserTest {
  implicit val print: ActionInterpret[Id, String] = {
    case Copy(from, to) => s"cp ${from.path} to ${to.path}"
    case Move(from, to) => s"mv ${from.path} to ${to.path}"
    case Remove(sources)   => s"rm ${sources.path}"
  }

  implicit val cmdSemi: Semigroup[Command] = new Semigroup[Command] {
    override def combine(x: Command, y: Command): Command = Command(
      s"${x.cmd} && ${y.cmd}"
    )
  }
}
