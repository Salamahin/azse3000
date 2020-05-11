package io.github.salamahin.azse3000

import java.net.URI

import cats.~>
import com.microsoft.azure.storage.{ResultContinuation, StorageCredentialsSharedAccessSignature}
import com.microsoft.azure.storage.blob.CloudBlobContainer
import io.github.salamahin.azse3000.shared._
import zio.{Task, UIO}

//object AzureEngineInterpreter extends (Azure ~> UIO) {
//  override def apply[A](fa: Azure[A]): UIO[A] =
//    fa match {
//      case StartListing(inPath, secret) =>
//        Task {
//          val cont = new CloudBlobContainer(
//            URI.create(s"htpps://${inPath.account}.windows.blob.core.net/${inPath.container}"),
//            new StorageCredentialsSharedAccessSignature(secret.secret)
//          )
//
//          cont.listBlobsSegmented(
//            inPath.prefix.path,
//            true,
//            null,
//            1000,
//            null,
//            null,
//            null
//          )
//        }.mapError {
//          AzureFailure(s"Failed to list blobs in $inPath", _)
//        }.either
//
//      case ContinueListing(tkn) => {
//
//      }
//
//      case IsCopied(blob) => ???
//
//      case RemoveBlob(blob) => ???
//
//      case SizeOfBlobBytes(blob) => ???
//
//      case StartCopy(src, blob, dst, dstSecret) => ???
//    }
//}
