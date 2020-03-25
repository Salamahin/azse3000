package com.aswatson.aswrdm.azse3000

import cats.data.EitherT
import cats.{Applicative, Monad}
import com.aswatson.aswrdm.azse3000.expression.ActionInterpret
import com.aswatson.aswrdm.azse3000.shared._

class Program[F[_]: Monad: Applicative, T, K](
  implicit
  endpoint: Endpoint[F, T, K],
  par: Parallel[F],
  fs: FileSystem[F, T, K]
) {

  import cats.instances.either._
  import cats.instances.vector._
  import cats.syntax.alternative._
  import cats.syntax.bifunctor._
  import cats.syntax.flatMap._
  import cats.syntax.functor._

  private def forEachBlobIn[U](path: FullPath)(action: T => F[Either[FileOperationFailed, U]]) =
    (for {
      container     <- EitherT.right[Issue with Aggregate](endpoint.toContainer(path))
      runActions    = fs.foreachBlob(container, path.relative) { par.traverseN(_)(action) }
      actionResults <- EitherT(runActions).leftWiden[Issue with Aggregate]

      (failed, succeed) = actionResults.toVector.separate
    } yield OperationResult(succeed.size, failed)).value

  private def copyBlobs(from: FullPath, to: FullPath) = forEachBlobIn(from) { fromBlob =>
    (for {
      toBlob <- EitherT.right[FileOperationFailed](endpoint.toBlob(to.resolve(from.relative)))
      _      <- EitherT(fs.copyContent(fromBlob, toBlob))
    } yield ()).value
  }

  private def moveBlobs(from: FullPath, to: FullPath) = forEachBlobIn(from) { fromBlob =>
    (for {
      toBlob <- EitherT.right[FileOperationFailed](endpoint.toBlob(to.resolve(from.relative)))
      _      <- EitherT(fs.copyContent(fromBlob, toBlob))
      _      <- EitherT(fs.remove(fromBlob))
    } yield ()).value
  }

  private def removeBlobs(path: FullPath) = forEachBlobIn(path) { fs.remove }

  private implicit val fsActionInterpret =
    new ActionInterpret[F, FullPath, Seq[(OperationDescription, Either[Issue with Aggregate, OperationResult])]] {
      override def run(term: Action[FullPath]) = term match {

        case Copy(fromPaths, to) =>
          par.traverse(fromPaths) { from =>
            for {
              ops      <- copyBlobs(from, to)
              showFrom <- endpoint.show(from)
              showTo   <- endpoint.show(to)
            } yield OperationDescription(s"Copy from $showFrom to $showTo") -> ops
          }

        case Move(fromPaths, to) =>
          par.traverse(fromPaths) { from =>
            for {
              ops      <- moveBlobs(from, to)
              showFrom <- endpoint.show(from)
              showTo   <- endpoint.show(to)
            } yield OperationDescription(s"Move from $showFrom to $showTo") -> ops
          }

        case Remove(fromPaths) =>
          par.traverse(fromPaths) { from =>
            for {
              ops      <- removeBlobs(from)
              showFrom <- endpoint.show(from)
            } yield OperationDescription(s"Remove from $showFrom") -> ops
          }
      }
    }

  def run(expression: Expression[FullPath]) =
    for {
      interpreted <- ActionInterpret.interpret(expression)
      (failures, succeeds) = interpreted
        .flatten
        .map {
          case (descr, opsResults) => opsResults.map(descr -> _)
        }
        .separate
    } yield if (failures.nonEmpty) Left(Failure(failures)) else Right(succeeds.toMap)
}
