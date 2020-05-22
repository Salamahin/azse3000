package io.github.salamahin.azse3000.interpret

import cats.~>
import io.github.salamahin.azse3000.shared._
import zio.clock.Clock
import zio.{UIO, URIO}

class VaultCompiler(knownCreds: Map[(Account, Container), Secret]) extends (Vault ~> URIO[Clock, *]) {
  override def apply[A](fa: Vault[A]) =
    fa match {
      case ReadCreds(acc, cont) => UIO { knownCreds.get((acc, cont)) }
    }
}
