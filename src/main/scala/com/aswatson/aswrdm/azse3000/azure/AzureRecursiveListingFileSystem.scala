package com.aswatson.aswrdm.azse3000.azure

import cats.Monad
import cats.data.EitherT
import com.aswatson.aswrdm.azse3000.program.Continuable
import com.aswatson.aswrdm.azse3000.shared.{Parallel, Prefix}
import com.microsoft.azure.storage.blob.{CloudBlobContainer, CloudBlobDirectory, CloudBlockBlob, ListBlobItem}

class AzureRecursiveListingFileSystem[F[_]: Monad](
  par: Parallel[F],
  continuable: Continuable[F]
) extends AzureFileSystem {
  override def foreachBlob[U](container: CloudBlobContainer, prefix: Prefix)(
    action: Seq[CloudBlockBlob] => F[Seq[U]]
  ): F[Either[Throwable, Seq[U]]] = {
    import cats.syntax.either._
    import cats.syntax.functor._

    import scala.collection.JavaConverters._

    def dirs(itms: Vector[CloudBlobDirectory]) = {
       itms
        .filter(_.isInstanceOf[CloudBlobDirectory])
        .map(_.asInstanceOf[CloudBlobDirectory])
    }

    def next(itms: Vector[ListBlobItem]) =
      EitherT
        .fromEither[F] {
          Either.catchNonFatal {
            dirs(itms) match {
              case IndexedSeq() => None
              case thisDir +: remainedDirs => Some(dirs(thisDir.listBlobs()))
            }
          }
        }
        .value

    def mapBlobs(itms: Vector[ListBlobItem]) = action {
      itms
        .filter(_.isInstanceOf[CloudBlockBlob])
        .map(_.asInstanceOf[CloudBlockBlob])
    }

    import scala.collection.JavaConverters._
    continuable
      .doAndContinue[Either[Throwable, (Vector[CloudBlobDirectory], Vector[CloudBlockBlob])], Either[Throwable, Seq[
        U
      ]]](
        () => next(separate(container.listBlobs(prefix.path).asScala.toVector)),
        prev => next(prev)
      )
      .map(_.flatten)

  }
}
