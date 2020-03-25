package com.aswatson.aswrdm.azse3000.azure

import java.net.URI

import com.aswatson.aswrdm.azse3000.shared.{Account, Container, Path}
import org.scalatest.{FunSuite, Matchers}

class AzureEndpointsTest extends FunSuite with Matchers {
  val tests = Seq(
    "https://acc.blob.core.windows.net/cont/p1"     -> (Account("acc"), Container("cont"), Path("p1")),
    "https://acc.blob.core.windows.net/cont/p1/p2"  -> (Account("acc"), Container("cont"), Path("p1/p2")),
    "https://acc.blob.core.windows.net/cont/p1.ext" -> (Account("acc"), Container("cont"), Path("p1.ext")),
    "https://acc.blob.core.windows.net/cont/p1/20200229_0319%40my-segment" -> (Account("acc"), Container("cont"), Path(
      "p1/20200229_0319@my-segment"))
  )

  tests.foreach {
    case (sample, expected) =>
      test(s"Should be able to decompose $sample") {
        AzureEndpoints.unapply(URI.create(sample)) should be(Some(expected))
      }
  }
}
