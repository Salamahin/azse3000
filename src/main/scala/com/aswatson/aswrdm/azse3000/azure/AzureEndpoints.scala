package com.aswatson.aswrdm.azse3000.azure

import java.net.URI

import cats.Monad
import com.aswatson.aswrdm.azse3000.shared.types.CREDS
import com.aswatson.aswrdm.azse3000.shared.{Endpoint, FullPath}
import com.microsoft.azure.storage.StorageCredentialsSharedAccessSignature
import com.microsoft.azure.storage.blob.{CloudBlobContainer, CloudBlockBlob}

class AzureEndpoints[F[_]: Monad](creds: CREDS) extends Endpoint[F, CloudBlockBlob, CloudBlobContainer] {
  override def toBlob(p: FullPath): F[CloudBlockBlob] = Monad[F].pure {
    val uri = URI.create(s"https://${p.account.name}.blob.core.windows.net/${p.container.name}/${p.relative.path}")
    new CloudBlockBlob(uri, new StorageCredentialsSharedAccessSignature(creds(p.account, p.container).secret))
  }

  override def toContainer(p: FullPath): F[CloudBlobContainer] = Monad[F].pure {
    val uri = URI.create(s"https://${p.account.name}.blob.core.windows.net/${p.container.name}")
    new CloudBlobContainer(uri, new StorageCredentialsSharedAccessSignature(creds(p.account, p.container).secret))
  }

  override def showBlob(p: CloudBlockBlob): F[String] = Monad[F].pure { p.getUri.toString }

  override def showContainer(p: CloudBlobContainer): F[String] = Monad[F].pure { p.getUri.toString }
}
