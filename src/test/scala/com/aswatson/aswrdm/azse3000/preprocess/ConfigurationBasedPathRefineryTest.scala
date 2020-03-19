package com.aswatson.aswrdm.azse3000.preprocess

import com.aswatson.aswrdm.azse3000.shared.Command
import org.scalatest.{FunSuite, Matchers}

class ConfigurationBasedPathRefineryTest extends FunSuite with Matchers {
  val knownHosts = Map(
    "known" -> "https://known.host"
  )

  Map(
    "rm cont@known:/p1/p2/p3"                        -> "rm https://known.host/cont/p1/p2/p3",
    "rm cont@unknown:/p1/p2/p3"                      -> "rm cont@unknown:/p1/p2/p3",
    "rm cont@known:/p1/p2/p3 cont@unknown:/p1/p2/p3" -> "rm https://known.host/cont/p1/p2/p3 cont@unknown:/p1/p2/p3"
  ).foreach {
    case (raw, expected) =>
      test(s"$raw should be refined to $expected") {
        ConfigurationBasedPathRefinery.refinePaths(knownHosts)(Command(raw)) shouldBe Command(expected)
      }
  }
}
