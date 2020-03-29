//package com.aswatson.aswrdm.azse3000.azure
//
//import cats.Monad
//import cats.data.EitherT
//import com.aswatson.aswrdm.azse3000.shared._
//import com.microsoft.azure.storage.blob.{CloudBlobContainer, CloudBlockBlob, ListBlobItem}
//import com.microsoft.azure.storage.{ResultContinuation, ResultSegment}
//
//class AzureFileSystem[F[_]: Monad](batchSize: Int)(
//  implicit
//  par: Parallel[F],
//  endpoint: Endpoint[F, CloudBlockBlob, CloudBlobContainer]
//) extends FileSystem[F, CloudBlockBlob, CloudBlobContainer] {
//
//  import cats.syntax.either._
//  import cats.syntax.functor._
//
//  override def foreachFile[U](cont: CloudBlobContainer, prefix: RelativePath)(
//    batchOperation: Seq[CloudBlockBlob] => F[Seq[U]]
//  ) = {
//
//    def continueListing(token: ResultContinuation) =
//      EitherT
//        .fromEither[F] {
//          Either.catchNonFatal(
//            cont.listBlobsSegmented(
//              prefix.path,
//              true,
//              null,
//              batchSize,
//              token,
//              null,
//              null
//            )
//          )
//        }
//        .leftSemiflatMap { e =>
//          endpoint
//            .showContainer(cont)
//            .map(show => FileSystemFailure(s"Failed to perform batch operation in $show ($prefix)", e))
//        }
//        .value
//
//    def moreBlobs(
//      maybeSegment: Either[FileSystemFailure, ResultSegment[ListBlobItem]],
//      acc: Vector[U]
//    ): F[Either[FileSystemFailure, Vector[U]]] = {
//      import scala.collection.JavaConverters._
//
//      Monad[F].tailRecM((maybeSegment, acc)) {
//        case (ms, a) =>
//          val maybeBlobs = ms
//            .map {
//              _.getResults
//                .asScala
//                .map(_.asInstanceOf[CloudBlockBlob])
//                .toVector
//            }
//
//          maybeBlobs match {
//            case Left(failure) => Monad[F].pure((failure.asLeft[Vector[U]]).asRight[Vector[U]])
//            case Right(blobs) =>
//          }
//
//          if (maybeBlobs.isLeft) {
//            val value = maybeBlobs.asRight[AAAA]
//
//            ???
//          } else if (maybeSegment.right.get.getHasMoreResults) {
//            ???
//          } else {
//            ???
//          }
//      }
//
//      //        if (!ms.getHasMoreResults) {
//      //          val aaaa = batchOperation(maybeBlobs).map(a ++ _).asRight[(ResultSegment[ListBlobItem], Vector[U])]
//      ////            .map(rr => (a ++ rr))
//      //
//      //          aaaa
//      //        }
//      //        else par
//      //          .zip(continueListing(ms.getContinuationToken), batchOperation(maybeBlobs))
//      //          .map {
//      //            case (nextSegment, processedBlobs) =>
//      //              val aaaa = nextSegment.map(ns => moreBlobs(ns, a ++ processedBlobs))
//      //
//      //            aaaa
//      //          }
//      //      }
//
//      ???
//    }
//
//    ???
//  }
//
////  override def copyContent(fromBlob: CloudBlockBlob, toBlob: CloudBlockBlob): F[Either[Throwable, Unit]] = {
//  //    import cats.syntax.either._
//  //
//  //    Monad[F].pure {
//  //      try {
//  //        val copy: Unit = toBlob.startCopy(fromBlob, null, true, null, null, null, null)
//  //        copy.asRight[Throwable]
//  //      } catch {
//  //        case e: Throwable => e.asLeft[Unit]
//  //      }
//  //    }
//  //  }
//  //
//  //  override def remove(blob: CloudBlockBlob): F[Either[Throwable, Unit]] = Monad[F].pure {
//  //    try {
//  //      blob.delete().asRight[Throwable]
//  //    } catch {
//  //      case e: Throwable => e.asLeft[Unit]
//  //    }
//  //  }
//}
