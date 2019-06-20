package com.aswatson.aswrdm.azse3000.azure

import java.net.{URI, URLDecoder}

import com.aswatson.aswrdm.azse3000.shared.{Account, ContainerName, Path, Secret}
import com.microsoft.azure.storage.StorageCredentialsSharedAccessSignature
import com.microsoft.azure.storage.blob.{CloudBlobContainer, CloudBlockBlob}

object AzureEndpoints {
  private val uriTemplate = "https://([a-zA-Z0-9]+).blob.core.windows.net/([a-zA-Z0-9-]+)/?(.*)".r

  def unapply(uri: URI): Option[(Account, ContainerName, Path)] = uri.toString match {
    case uriTemplate(acc, cont, path) => Some(Account(acc), ContainerName(cont), Path(URLDecoder.decode(path, "UTF-8")))
    case _                            => None
  }

  def toContainer(account: Account, container: ContainerName, token: Secret) = new CloudBlobContainer(
    URI.create(s"https://${account.name}.blob.core.windows.net/${container.name}"),
    new StorageCredentialsSharedAccessSignature(token.secret)
  )

  def toFile(path: Path, token: Secret) = new CloudBlockBlob(
    URI.create(path.path),
    new StorageCredentialsSharedAccessSignature(token.secret)
  )
}
