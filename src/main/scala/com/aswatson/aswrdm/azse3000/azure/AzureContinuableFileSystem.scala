package com.aswatson.aswrdm.azse3000.azure

import cats.Monad
import cats.data.EitherT
import com.aswatson.aswrdm.azse3000.program.Continuable
import com.aswatson.aswrdm.azse3000.shared._
import com.microsoft.azure.storage.blob.{CloudBlobContainer, CloudBlockBlob, ListBlobItem}
import com.microsoft.azure.storage.{ResultContinuation, ResultSegment}

class AzureContinuableFileSystem[F[_]: Monad](
  batchSize: Int,
  endpoint: Endpoint[F, CloudBlockBlob, CloudBlobContainer],
  continuable: Continuable[F]
) extends FileSystem[F, CloudBlockBlob, CloudBlobContainer] {

  import cats.syntax.either._
  import cats.syntax.functor._

  private def sequence[A, B](s: Seq[Either[A, B]]): Either[A, Seq[B]] =
    s.foldRight(Right(Nil): Either[A, Seq[B]]) { (e, acc) =>
      for (xs <- acc.right; x <- e.right) yield x +: xs
    }

  override def foreachBlob[U](cont: CloudBlobContainer, prefix: Prefix)(
    batchOperation: Seq[CloudBlockBlob] => F[Seq[U]]
  ): F[Either[Throwable, Seq[U]]] = {

    def continueListing(token: ResultContinuation) =
      EitherT
        .fromEither[F] {
          Either.catchNonFatal(
            cont.listBlobsSegmented(
              prefix.path,
              true,
              null,
              batchSize,
              token,
              null,
              null
            )
          )
        }
        .value

    def getBlobs(rs: ResultSegment[ListBlobItem]) = {
      import scala.collection.JavaConverters._

      rs.getResults
        .asScala
        .map(_.asInstanceOf[CloudBlockBlob])
        .toVector
    }

    continuable
      .doAnd[Either[Throwable, ResultSegment[ListBlobItem]], Either[Throwable, Seq[U]]](
        () => continueListing(null), {
          case Right(segment) if segment.getHasMoreResults => continueListing(segment.getContinuationToken).map(Some(_))
          case _                                           => Monad[F].pure(None)
        },
        rs =>
          EitherT
            .fromEither[F](rs)
            .semiflatMap { rs =>
              batchOperation(getBlobs(rs))
            }
            .value
      )
      .map(sequence)
      .map(x => x.map(_.flatten))
  }

  override def copyContent(fromBlob: CloudBlockBlob, toBlob: CloudBlockBlob): F[Either[Throwable, Unit]] = {
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

  override def remove(blob: CloudBlockBlob): F[Either[Throwable, Unit]] = Monad[F].pure {
    try {
      blob.delete().asRight
    } catch {
      case e: Throwable => e.asLeft
    }
  }
}
