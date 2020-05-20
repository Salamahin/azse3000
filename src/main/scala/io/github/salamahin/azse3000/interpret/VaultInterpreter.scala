package io.github.salamahin.azse3000.interpret

import cats.~>
import io.github.salamahin.azse3000.shared.{Container, ReadCreds, Secret, Vault}
import zio.UIO

class VaultInterpreter(knownCreds: Map[(String, Container), Secret]) extends (Vault ~> UIO) {
  override def apply[A](fa: Vault[A]): UIO[A] =
    fa match {
      case ReadCreds(acc, cont) => UIO { knownCreds.get((acc, cont)) }
    }
}
