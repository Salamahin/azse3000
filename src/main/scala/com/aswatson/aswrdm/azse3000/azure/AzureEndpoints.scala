package com.aswatson.aswrdm.azse3000.azure

import java.net.URI

import cats.Monad
import com.aswatson.aswrdm.azse3000.shared.types.CREDS
import com.aswatson.aswrdm.azse3000.shared.{Endpoint, ParsedPath, Path}
import com.microsoft.azure.storage.StorageCredentialsSharedAccessSignature
import com.microsoft.azure.storage.blob.{CloudBlobContainer, CloudBlockBlob}

class AzureEndpoints[F[_]: Monad](creds: CREDS) extends Endpoint[F, CloudBlockBlob, CloudBlobContainer] {
  private def parsedPathToUri(p: ParsedPath) =
    s"https://${p.account.name}.blob.core.windows.net/${p.container.name}/${p.prefix.path}"

  override def toBlob(p: ParsedPath): F[CloudBlockBlob] = Monad[F].pure {
    val uri = URI.create(parsedPathToUri(p))
    new CloudBlockBlob(uri, new StorageCredentialsSharedAccessSignature(creds(p.account, p.container).secret))
  }

  override def toContainer(p: ParsedPath): F[CloudBlobContainer] = Monad[F].pure {
    val uri = URI.create(s"https://${p.account.name}.blob.core.windows.net/${p.container.name}")
    new CloudBlobContainer(uri, new StorageCredentialsSharedAccessSignature(creds(p.account, p.container).secret))
  }

  override def blobPath(p: CloudBlockBlob): F[Path] = Monad[F].pure { Path(p.getUri.toString) }

  override def containerPath(p: CloudBlobContainer): F[Path] = Monad[F].pure { Path(p.getUri.toString) }

  override def showPath(p: ParsedPath): F[String] = Monad[F].pure { parsedPathToUri(p) }
}
