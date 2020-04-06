package com.aswatson.aswrdm.azse3000.azure

import cats.Monad
import cats.data.EitherT
import com.aswatson.aswrdm.azse3000.shared.{Parallel, Prefix}
import com.microsoft.azure.storage.blob.{CloudBlobContainer, CloudBlobDirectory, CloudBlockBlob, ListBlobItem}

class AzureRecursiveListingFileSystem[F[_]: Monad](
  par: Parallel[F]
) extends AzureFileSystem {
  override def foreachBlob[U](container: CloudBlobContainer, prefix: Prefix)(
    action: Seq[CloudBlockBlob] => F[Seq[U]]
  ): F[Either[Throwable, Seq[U]]] = {
    import cats.syntax.either._

    import scala.collection.JavaConverters._

    def init() = {
      EitherT
        .fromEither {
          Either.catchNonFatal {
            val itms  = container.listBlobs(prefix.path).asScala.toVector
            val dirs  = itms.filter(_.isInstanceOf[CloudBlobDirectory]).map(_.asInstanceOf[CloudBlobDirectory])
            val blobs = itms.filter(_.isInstanceOf[CloudBlockBlob]).map(_.asInstanceOf[CloudBlockBlob])

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

                val subdirs = childrenOfDir
                  .filter(_.isInstanceOf[CloudBlobDirectory])
                  .map(_.asInstanceOf[CloudBlobDirectory])

                val blobsInDir = childrenOfDir
                  .filter(_.isInstanceOf[CloudBlockBlob])
                  .map(_.asInstanceOf[CloudBlockBlob])

                Some(subdirs ++ remainingDirs, blobsInDir)
            }
          }
        }
    }

    new Continuable(eitherTPar(par))
      .doAndContinue[(Vector[CloudBlobDirectory], Vector[CloudBlockBlob]), Seq[U]](
        () => init(),
        dirsAndBlobs => next(dirsAndBlobs._1),
        dirsAndBlobs => EitherT.right[Throwable](action(dirsAndBlobs._2))
      )
      .map(_.flatten)
      .value
  }
}
