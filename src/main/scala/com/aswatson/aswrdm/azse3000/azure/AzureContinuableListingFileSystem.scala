package com.aswatson.aswrdm.azse3000.azure

import cats.Monad
import cats.data.EitherT
import com.aswatson.aswrdm.azse3000.program.Continuable
import com.aswatson.aswrdm.azse3000.shared._
import com.microsoft.azure.storage.blob.{CloudBlobContainer, CloudBlockBlob, ListBlobItem}
import com.microsoft.azure.storage.{ResultContinuation, ResultSegment}

class AzureContinuableListingFileSystem[F[_]: Monad](
  batchSize: Int,
  endpoint: Endpoint[F, CloudBlockBlob, CloudBlobContainer],
  continuable: Continuable[EitherT[F, Throwable, *]]
) extends AzureFileSystem {

  import cats.syntax.either._

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

    def getBlobs(rs: ResultSegment[ListBlobItem]) = {
      import scala.collection.JavaConverters._

      rs.getResults
        .asScala
        .map(_.asInstanceOf[CloudBlockBlob])
        .toVector
    }

    continuable
      .doAndContinue[ResultSegment[ListBlobItem], Seq[U]](
        () => continueListing(null),
        rs =>
          if (rs.getHasMoreResults) continueListing(rs.getContinuationToken).map(x => Option(x))
          else EitherT.pure[F, Throwable](None),
        rs => EitherT.right[Throwable](batchOperation(getBlobs(rs)))
      )
      .map(x => x.flatten)
      .value
  }
}
