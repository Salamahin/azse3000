//package io.github.salamahin.azse3000.expression
//
//import cats.Id
//import cats.kernel.Semigroup
//import io.github.salamahin.azse3000.shared._
//import org.scalatest.funsuite.AnyFunSuite
//import org.scalatest.matchers.should.Matchers
//
//class CommandParserTest extends AnyFunSuite with Matchers {
//  import CommandParserTest._
//
//  private val primitives = Seq(
//    Command("cp a1 a2 a3 b")  -> Copy(Seq("a1", "a2", "a3").map(Path), Path("b")),
//    Command("mv a1 a2 a3 b")  -> Move(Seq("a1", "a2", "a3").map(Path), Path("b")),
//    Command("rm a1 a2 a3")    -> Remove(Seq("a1", "a2", "a3").map(Path)),
//    Command("count a1 a2 a3") -> Count(Seq("a1", "a2", "a3").map(Path))
//  )
//
//  primitives.foreach {
//    case (command, expectedTree) =>
//      test(s"should parse `$command`") {
//        CommandParser(command) match {
//          case Right(c) => c should be(expectedTree)
//          case Left(v)  => fail(s"Failed to parse tree: $v")
//        }
//      }
//  }
//
//  test("should parse `and` expression") {
//    val expr = Semigroup.combineAllOption(primitives.map(_._1))
//
//    CommandParser(expr.get).map(x => ActionInterpret.interpret(x)) match {
//      case Right(c: Vector[String]) =>
//        c should contain inOrderOnly (
//          "cp a1, a2, a3 to b",
//          "mv a1, a2, a3 to b",
//          "rm a1, a2, a3",
//          "count a1, a2, a3"
//        )
//      case Left(v) => fail(s"Failed to parse tree: $v")
//    }
//  }
//}
//
//object CommandParserTest {
//  implicit val print: ActionInterpret[String] = {
//    case Copy(from, to) => s"cp ${from.map(_.path).mkString(", ")} to ${to.path}"
//    case Move(from, to) => s"mv ${from.map(_.path).mkString(", ")} to ${to.path}"
//    case Remove(from)   => s"rm ${from.map(_.path).mkString(", ")}"
//    case Count(int)     => s"count ${int.map(_.path).mkString(", ")}"
//  }
//
//  implicit val cmdSemi: Semigroup[Command] = (x: Command, y: Command) =>
//    Command(
//      s"${x.cmd} && ${y.cmd}"
//    )
//}
