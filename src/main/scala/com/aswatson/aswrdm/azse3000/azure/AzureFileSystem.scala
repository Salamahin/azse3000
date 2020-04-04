package com.aswatson.aswrdm.azse3000.azure

import cats.Monad
import com.aswatson.aswrdm.azse3000.shared.FileSystem
import com.microsoft.azure.storage.blob.{CloudBlobContainer, CloudBlockBlob}

abstract class AzureFileSystem[F[_]: Monad] extends FileSystem[F, CloudBlockBlob, CloudBlobContainer] {
  import cats.syntax.either._

  final override def copyContent(fromBlob: CloudBlockBlob, toBlob: CloudBlockBlob): F[Either[Throwable, Unit]] = {
    import cats.syntax.either._

    Monad[F].pure {
      try {
        val copy: Unit = toBlob.startCopy(fromBlob, null, true, null, null, null, null)
        copy.asRight
      } catch {
        case e: Throwable => e.asLeft
      }
    }
  }

  final override def remove(blob: CloudBlockBlob): F[Either[Throwable, Unit]] = Monad[F].pure {
    try {
      blob.delete().asRight
    } catch {
      case e: Throwable => e.asLeft
    }
  }
}
