package com.aswatson.aswrdm.azse3000.expression

import cats.Id
import cats.kernel.Semigroup
import com.aswatson.aswrdm.azse3000.shared._
import org.scalatest.{FunSuite, Matchers}

class InputParserTest extends FunSuite with Matchers {
  import InputParserTest._

  private val primitives = Seq(
    Command("cp a1 a2 a3 b") -> Copy(Seq("a1", "a2", "a3").map(Path), Path("b")),
    Command("mv a1 a2 a3 b") -> Move(Seq("a1", "a2", "a3").map(Path), Path("b")),
    Command("rm a1 a2 a3")   -> Remove(Seq("a1", "a2", "a3").map(Path))
  )

  primitives.foreach {
    case (command, expectedTree) =>
      test(s"should parse `$command`") {
        InputParser(command) match {
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
          "cp a1, a2, a3 to b",
          "mv a1, a2, a3 to b",
          "rm a1, a2, a3"
        )
      case Left(v) => fail(s"Failed to parse tree: $v")
    }
  }
}

object InputParserTest {
  implicit val print: ActionInterpret[Id, String] = {
    case Copy(sources, to) => s"cp ${sources.map(_.path).mkString(", ")} to ${to.path}"
    case Move(sources, to) => s"mv ${sources.map(_.path).mkString(", ")} to ${to.path}"
    case Remove(sources)   => s"rm ${sources.map(_.path).mkString(", ")}"
  }

  implicit val cmdSemi: Semigroup[Command] = (x: Command, y: Command) => Command(
    s"${x.cmd} && ${y.cmd}"
  )
}
