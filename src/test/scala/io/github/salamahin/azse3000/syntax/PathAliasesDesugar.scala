package io.github.salamahin.azse3000.syntax

import io.github.salamahin.azse3000.shared.Command
import org.scalatest.{FunSuite, Matchers}

class PathAliasesDesugar extends FunSuite with Matchers {
  val knownHosts = Map(
    "known" -> "https://known.host"
  )

  Map(
    "rm cont@known:/p1/p2/p3"                                              -> "rm https://known.host/cont/p1/p2/p3",
    "rm cont@known:/etl/d_hierarchy/data/entity_set=dimensions-store-hier" -> "rm https://known.host/cont/etl/d_hierarchy/data/entity_set=dimensions-store-hier",
    "rm cont@unknown:/p1/p2/p3"                                            -> "rm cont@unknown:/p1/p2/p3",
    "rm cont@known:/p1/p2/p3 cont@unknown:/p1/p2/p3"                       -> "rm https://known.host/cont/p1/p2/p3 cont@unknown:/p1/p2/p3"
  ).foreach {
    case (raw, expected) =>
      test(s"$raw should be refined to $expected") {
        ToUrlFormat.refine(knownHosts)(Command(raw)) shouldBe Command(expected)
      }
  }
}
