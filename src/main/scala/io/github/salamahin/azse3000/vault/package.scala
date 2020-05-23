package io.github.salamahin.azse3000
import cats.InjectK
import io.github.salamahin.azse3000.shared.{Account, Container, Secret}

package object vault {
  sealed trait VaultOps[T]
  final case class ReadCreds(acc: Account, cont: Container) extends VaultOps[Option[Secret]]

  final class Vault[F[_]]()(implicit inj: InjectK[VaultOps, F]) {
    def readCreds(acc: Account, cont: Container) = inj(ReadCreds(acc, cont))
  }

  object Vault {
    implicit def vaultStorage[F[_]](implicit I: InjectK[VaultOps, F]): Vault[F] = new Vault[F]
  }
}
