package io.github.salamahin.azse3000.interpret

import cats.~>
import io.github.salamahin.azse3000.shared._
import zio.UIO

class VaultInterpreter(knownCreds: Map[(Account, Container), Secret]) extends (Vault ~> UIO) {
  override def apply[A](fa: Vault[A]): UIO[A] =
    fa match {
      case ReadCreds(acc, cont) => UIO { knownCreds.get((acc, cont)) }
    }
}
