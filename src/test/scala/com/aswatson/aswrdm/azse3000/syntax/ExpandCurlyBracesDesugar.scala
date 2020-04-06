package com.aswatson.aswrdm.azse3000.syntax

import com.aswatson.aswrdm.azse3000.shared.Command
import org.scalatest.{FunSuite, Matchers}

class ExpandCurlyBracesDesugar extends FunSuite with Matchers {
  Map(
    "cp cont@host:/{from,to}" -> "cp cont@host:/from cont@host:/to",
    "cp cont@known:/{from,to}/p1/p2" -> "cp cont@known:/from/p1/p2 cont@known:/to/p1/p2",
    "rm {c1,c2}@host:/p1/p2" -> "rm c1@host:/p1/p2 c2@host:/p1/p2"
  ).foreach {
    case (raw, expected) =>
      test(s"$raw should be refined to $expected") {
        ExpandCurlyBraces.expand(Command(raw)) shouldBe Command(expected)
      }
  }
}
