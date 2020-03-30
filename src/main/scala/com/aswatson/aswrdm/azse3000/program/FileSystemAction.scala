package com.aswatson.aswrdm.azse3000.program

import cats.Monad
import cats.data.EitherT
import com.aswatson.aswrdm.azse3000.expression.ActionInterpret
import com.aswatson.aswrdm.azse3000.shared._

class FileSystemAction[F[_]: Monad, B, K](
  endpoint: Endpoint[F, B, K],
  par: Parallel[F],
  fs: FileSystem[F, B, K]
) {

  import cats.instances.either._
  import cats.instances.vector._
  import cats.syntax.alternative._
  import cats.syntax.bifunctor._
  import cats.syntax.functor._
  import cats.syntax.flatMap._

  private def relativize(what: B, from: ParsedPath, to: ParsedPath): F[ParsedPath] = {
    for {
      p     <- endpoint.blobPath(what)
      begin = p.path.indexOf(from.relative.path) + from.relative.path.length
      relative = p.path.substring(begin + 1)
      newRelative = s"${to.relative.path}/${relative}"
    } yield to.copy(relative = RelativePath(newRelative))
  }

  private def forEachBlobIn[U](path: ParsedPath)(action: B => F[Either[OperationFailure, U]]) =
    (for {
      container     <- EitherT.right[Fatal with Aggregate](endpoint.toContainer(path))
      runActions    = fs.foreachBlob(container, path.relative) { par.traverse(_)(action) }
      actionResults <- EitherT(runActions).leftWiden[Fatal with Aggregate]

      (failed, succeed) = actionResults.toVector.separate
    } yield OperationResult(succeed.size, failed)).value

  private def copyBlobs(from: ParsedPath, to: ParsedPath) = forEachBlobIn(from) { fromBlob =>
    (for {
      toBlobPath <- EitherT.right[OperationFailure](relativize(fromBlob, from, to))
      toBlob     <- EitherT.right[OperationFailure](endpoint.toBlob(toBlobPath))
      _          <- EitherT(fs.copyContent(fromBlob, toBlob))
    } yield ()).value
  }

  private def moveBlobs(from: ParsedPath, to: ParsedPath) = forEachBlobIn(from) { fromBlob =>
    (for {
      toBlobPath <- EitherT.right[OperationFailure](relativize(fromBlob, from, to))
      toBlob     <- EitherT.right[OperationFailure](endpoint.toBlob(toBlobPath))
      _          <- EitherT(fs.copyContent(fromBlob, toBlob))
      _          <- EitherT(fs.remove(fromBlob))
    } yield ()).value
  }

  private def removeBlobs(path: ParsedPath) = forEachBlobIn(path) { fs.remove }

  private val runFsAction =
    new ActionInterpret[F, ParsedPath, Seq[(OperationDescription, Either[Fatal with Aggregate, OperationResult])]] {
      override def run(term: Action[ParsedPath]) = term match {

        case Copy(fromPaths, to) =>
          par.traverse(fromPaths) { from =>
            for {
              ops      <- copyBlobs(from, to)
              showFrom <- endpoint.showPath(from)
              showTo   <- endpoint.showPath(to)
            } yield OperationDescription(s"Copy from $showFrom to $showTo") -> ops
          }

        case Move(fromPaths, to) =>
          par.traverse(fromPaths) { from =>
            for {
              ops      <- moveBlobs(from, to)
              showFrom <- endpoint.showPath(from)
              showTo   <- endpoint.showPath(to)
            } yield OperationDescription(s"Move from $showFrom to $showTo") -> ops
          }

        case Remove(fromPaths) =>
          par.traverse(fromPaths) { from =>
            for {
              ops      <- removeBlobs(from)
              showFrom <- endpoint.showPath(from)
            } yield OperationDescription(s"Remove from $showFrom") -> ops
          }
      }
    }

  def evaluate(
    expression: Expression[ParsedPath]
  ): F[Either[AggregatedFatal, Map[OperationDescription, OperationResult]]] =
    for {
      interpreted <- ActionInterpret.interpret(expression)(Monad[F], runFsAction)
      (failures, succeeds) = interpreted
        .flatten
        .map {
          case (descr, opsResults) => opsResults.map(descr -> _)
        }
        .separate
    } yield if (failures.nonEmpty) Left(AggregatedFatal(failures)) else Right(succeeds.toMap)
}
