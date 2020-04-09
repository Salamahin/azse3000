package com.aswatson.aswrdm.azse3000.azure

import java.net.URI
import java.util
import java.util.Date

import com.aswatson.aswrdm.azse3000.shared.types.CREDS
import com.aswatson.aswrdm.azse3000.shared.{Account, Container, Secret}
import com.dimafeng.testcontainers.{FixedHostPortGenericContainer, Container => DockerContainer, ForAllTestContainer => ForAllTestDockerContainer}
import com.microsoft.azure.storage.blob._
import com.microsoft.azure.storage.{StorageCredentialsAccountAndKey, StorageCredentialsSharedAccessSignature}
import org.scalatest.{FlatSpec, Matchers}

class AzuriteContainerSpec extends FlatSpec with ForAllTestDockerContainer with Matchers {
  private val storageAcc      = "devstoreaccount1"
  private val storageKey      = "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw=="
  private val blobStoragePort = 10000

  override def container: DockerContainer = FixedHostPortGenericContainer(
    "mcr.microsoft.com/azure-storage/azurite",
    exposedHostPort = blobStoragePort,
    exposedContainerPort = blobStoragePort,
    command = Seq("azurite-blob", "--blobHost", "0.0.0.0")
  )

  private var creds: CREDS  = Map.empty
  private val blobContainer = Container("container1")

  class AzuritePath(val path: String) {
    def resolve(other: String) = new AzuritePath(s"$path/$other")
    def uri = URI.create(path)
  }

  private def azuriteEndpoint = new AzuritePath(s"http://127.0.0.1:$blobStoragePort/$storageAcc")

  private def createBlobContainer(cont: Container) = {
    val client = new CloudBlobClient(
      azuriteEndpoint.uri,
      new StorageCredentialsAccountAndKey(
        storageAcc,
        storageKey
      )
    )

    val cloudContainer = client.getContainerReference(cont.name)
    cloudContainer.createIfNotExists()

    val policy = new SharedAccessBlobPolicy()
    policy.setPermissions(util.EnumSet.allOf(classOf[SharedAccessBlobPermissions]))
    policy.setSharedAccessExpiryTime(new Date(Long.MaxValue))

    val sas = cloudContainer.generateSharedAccessSignature(
      policy,
      null
    )

    creds += ((Account(storageAcc), cont) -> Secret(sas))
  }

  private def touch(cont: Container, relative: String) = {
    val cloudContainer = new CloudBlobContainer(
      azuriteEndpoint.resolve(cont.name).uri,
      new StorageCredentialsSharedAccessSignature(
        creds(Account(storageAcc), cont).secret
      )
    )

    cloudContainer
      .getBlockBlobReference(relative)
      .uploadText("")
  }

  private def find(cont: Container) = {
    val cloudContainer = new CloudBlobContainer(
      azuriteEndpoint.resolve(cont.name).uri,
      new StorageCredentialsSharedAccessSignature(
        creds(Account(storageAcc), cont).secret
      )
    )

    import scala.collection.JavaConverters._

    cloudContainer
      .listBlobs(null, true)
      .asScala
      .map(_.asInstanceOf[CloudBlockBlob])
      .map(_.getUri.toString)
  }

  "an environment" should "have a container" in {
    createBlobContainer(blobContainer)
  }

  "some files" should "exists in this container" in {
    val f1 = "initial/f1"
    val f2 = "initial/f2"
    val f3 = "initial/f3"

    Seq(f1, f2, f3).foreach(touch(blobContainer, _))

    find(blobContainer) should contain only (
      azuriteEndpoint.resolve(blobContainer.name).resolve(f1).path,
      azuriteEndpoint.resolve(blobContainer.name).resolve(f2).path,
      azuriteEndpoint.resolve(blobContainer.name).resolve(f3).path
    )
  }
}
