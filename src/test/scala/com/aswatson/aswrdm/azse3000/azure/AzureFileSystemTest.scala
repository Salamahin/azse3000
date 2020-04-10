package com.aswatson.aswrdm.azse3000.azure

import java.net.URI

import cats.Id
import com.aswatson.aswrdm.azse3000.azure.AzureFileSystemTest.AzuritePath
import com.aswatson.aswrdm.azse3000.program
import com.aswatson.aswrdm.azse3000.shared.Prefix
import com.dimafeng.testcontainers.{
  FixedHostPortGenericContainer,
  Container => DockerContainer,
  ForAllTestContainer => ForAllTestDockerContainer
}
import com.microsoft.azure.storage.StorageCredentialsAccountAndKey
import com.microsoft.azure.storage.blob._
import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}

object AzureFileSystemTest {
  class AzuritePath(val path: String) {
    def resolve(other: String) = new AzuritePath(s"$path/$other")
    def uri                    = URI.create(path)
  }
}

class AzureFileSystemTest extends FunSuite with ForAllTestDockerContainer with Matchers with BeforeAndAfter {
  private val storageAcc      = "devstoreaccount1"
  private val storageKey      = "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw=="
  private val blobStoragePort = 10000

  override def container: DockerContainer = FixedHostPortGenericContainer(
    "mcr.microsoft.com/azure-storage/azurite",
    exposedHostPort = blobStoragePort,
    exposedContainerPort = blobStoragePort,
    command = Seq("azurite-blob", "--blobHost", "0.0.0.0")
  )

  private def azuriteEndpoint = new AzuritePath(s"http://127.0.0.1:$blobStoragePort/$storageAcc")

  private def createBlobContainer(name: String) = {
    val client = new CloudBlobClient(
      azuriteEndpoint.uri,
      new StorageCredentialsAccountAndKey(
        storageAcc,
        storageKey
      )
    )

    val cloudContainer = client.getContainerReference(name)
    cloudContainer.createIfNotExists()

    cloudContainer
  }

  private def touch(cont: CloudBlobContainer, relatives: String*) = {
    relatives
      .map {
        cont
          .getBlockBlobReference(_)
          .uploadText("")
      }
  }

  private def collectPaths(blobs: Seq[CloudBlockBlob]) = blobs.map(_.getUri.toString)

  var blobContainer: CloudBlobContainer = _
  val containerName                     = "flat-listing"
  val fileA                             = "folder1/a"
  val fileB                             = "folder1/folder2/b"
  val fileC                             = "folder1/folder2/c"

  before {
    blobContainer = createBlobContainer(containerName)
    touch(blobContainer, fileA, fileB, fileC)
  }

  Map(
    "recursive fs"    -> new AzureRecursiveListingFileSystem[Id](program.parId),
    "flat listing fs" -> new AzureFlatListingFileSystem[Id](1, program.parId)
  ).foreach {
    case (descr, fs) =>
      test(s"$descr is able to fetch files in nested dirs") {
        val foundBlobs = fs.foreachBlob(blobContainer, Prefix("folder1"))(collectPaths)
        foundBlobs should be('right)
        foundBlobs.right.get should contain only (
          azuriteEndpoint.resolve(containerName).resolve(fileA).path,
          azuriteEndpoint.resolve(containerName).resolve(fileB).path,
          azuriteEndpoint.resolve(containerName).resolve(fileC).path,
        )
      }
  }
}
