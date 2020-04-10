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
import org.scalatest.{FunSuite, Matchers}

object AzureFileSystemTest {
  class AzuritePath(val path: String) {
    def resolve(other: String) = new AzuritePath(s"$path/$other")
    def uri                    = URI.create(path)
  }
}

class AzureFileSystemTest extends FunSuite with ForAllTestDockerContainer with Matchers {
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

  private def find(cont: CloudBlobContainer) = {
    import scala.collection.JavaConverters._

    cont
      .listBlobs(null, true)
      .asScala
      .map(_.asInstanceOf[CloudBlockBlob])
      .map(_.getUri.toString)
  }

  private def collectPaths(blobs: Seq[CloudBlockBlob]) = blobs.map(_.getUri.toString)

  test("Recursive file system should be able to fetch all files in nested dirs") {
    val containerName = "flat-listing"
    val fileA         = "folder1/a"
    val fileB         = "folder1/folder2/b"
    val fileC         = "folder1/folder2/c"

    val cont = createBlobContainer(containerName)
    touch(cont, fileA, fileB, fileC)

    val recursiveFs = new AzureRecursiveListingFileSystem[Id](program.parId)

    val foundBlobs = recursiveFs.foreachBlob(cont, Prefix("folder1"))(collectPaths)
    foundBlobs should be('right)
    foundBlobs.right.get should contain only (
      azuriteEndpoint.resolve(containerName).resolve(fileA).path,
      azuriteEndpoint.resolve(containerName).resolve(fileB).path,
      azuriteEndpoint.resolve(containerName).resolve(fileC).path,
    )
  }

//  test("Recursive file system should be able to fetch all files in nested dirs") {
//    val containerName = "flat-listing"
//    val fileA         = "folder1/a"
//    val fileB         = "folder1/folder2/b"
//    val fileC         = "folder1/folder2/c"
//
//    val cont = createBlobContainer(containerName)
//    touch(cont, fileA, fileB, fileC)
//
//    val recursiveFs = new AzureFlatListingFileSystem[Id](1, program.parId)
//
//    val foundBlobs = recursiveFs.foreachBlob(cont, Prefix("folder1"))(collectPaths)
//    foundBlobs should be('right)
//    foundBlobs.right.get should contain only (
//      azuriteEndpoint.resolve(containerName).resolve(fileA).path,
//      azuriteEndpoint.resolve(containerName).resolve(fileB).path,
//      azuriteEndpoint.resolve(containerName).resolve(fileC).path,
//    )
//  }

}
