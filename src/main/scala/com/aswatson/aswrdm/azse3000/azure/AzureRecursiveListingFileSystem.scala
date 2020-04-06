package com.aswatson.aswrdm.azse3000.azure

import cats.Monad
import cats.data.EitherT
import com.aswatson.aswrdm.azse3000.program.Continuable
import com.aswatson.aswrdm.azse3000.shared.{Parallel, Prefix}
import com.microsoft.azure.storage.blob.{CloudBlobContainer, CloudBlobDirectory, CloudBlockBlob, ListBlobItem}

class AzureRecursiveListingFileSystem[F[_]: Monad](
  par: Parallel[F],
  continuable: Continuable[EitherT[F, Throwable, *]]
) extends AzureFileSystem {
  override def foreachBlob[U](container: CloudBlobContainer, prefix: Prefix)(
    action: Seq[CloudBlockBlob] => F[Seq[U]]
  ): F[Either[Throwable, Seq[U]]] = {
    import cats.syntax.either._

    import scala.collection.JavaConverters._

    def filterOfType[T](itms: Vector[ListBlobItem]) = {
      itms
        .filter(_.isInstanceOf[T])
        .map(_.asInstanceOf[T])
    }

    def init() = {
      EitherT
        .fromEither {
          Either.catchNonFatal {
            val itms  = container.listBlobs(prefix.path).asScala.toVector
            val dirs  = filterOfType[CloudBlobDirectory](itms)
            val blobs = filterOfType[CloudBlockBlob](itms)

            (dirs, blobs)
          }
        }
    }

    def next(dirs: Vector[CloudBlobDirectory]) = {
      EitherT
        .fromEither {
          Either.catchNonFatal {
            dirs match {
              case IndexedSeq() => None
              case dir +: remainingDirs =>
                val childrenOfDir = dir.listBlobs().asScala.toVector
                val subdirs       = filterOfType[CloudBlobDirectory](childrenOfDir)
                val blobsInDir    = filterOfType[CloudBlockBlob](childrenOfDir)

                Some(subdirs ++ remainingDirs, blobsInDir)
            }
          }
        }
    }

    continuable
      .doAndContinue[(Vector[CloudBlobDirectory], Vector[CloudBlockBlob]), Seq[U]](
        () => init(),
        dirsAndBlobs => next(dirsAndBlobs._1),
        dirsAndBlobs => EitherT.right[Throwable](action(dirsAndBlobs._2))
      )
      .map(_.flatten)
      .value
  }
}
