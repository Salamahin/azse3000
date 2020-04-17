package io.github.salamahin.azse3000.syntax

import cats.Id
import io.github.salamahin.azse3000.shared.Command
import org.scalatest.{FunSuite, Matchers}

class ShellLikeSyntaxTest extends FunSuite with Matchers {
  val syntax = new ShellLikeSyntax[Id](
    Map(
      "acc"  -> "http://myacc.com",
      "acc1" -> "http://myacc1.com",
      "acc2" -> "http://myacc2.com"
    )
  )

  Map(
    "rm cont@acc:/p1/p2@appendix/key=value" -> "rm http://myacc.com/cont/p1/p2@appendix/key=value",
    "rm cont@{acc1,acc2}:/{p1,p2,p3}"       -> "rm http://myacc1.com/cont/p1 http://myacc1.com/cont/p2 http://myacc1.com/cont/p3 http://myacc2.com/cont/p1 http://myacc2.com/cont/p2 http://myacc2.com/cont/p3"
  ).foreach {
    case (input, expected) =>
      test(s"can desugar $input") {
        syntax.desugar(Command(input)).cmd should be(expected)
      }
  }
}
