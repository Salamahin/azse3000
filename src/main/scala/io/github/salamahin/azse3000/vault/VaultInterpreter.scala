package io.github.salamahin.azse3000.vault

import cats.~>
import io.github.salamahin.azse3000.shared._
import zio.clock.Clock
import zio.{UIO, URIO}

class VaultInterpreter(knownCreds: Map[(Account, Container), Secret]) extends (VaultOps ~> URIO[Clock, *]) {
  override def apply[A](fa: VaultOps[A]) =
    fa match {
      case ReadCreds(acc, cont) => UIO { knownCreds.get((acc, cont)) }
    }
}
