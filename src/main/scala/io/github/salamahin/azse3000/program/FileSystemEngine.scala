package io.github.salamahin.azse3000.program

import cats.Monad
import cats.data.EitherT
import io.github.salamahin.azse3000.expression.ActionInterpret
import io.github.salamahin.azse3000.shared._

import scala.annotation.tailrec

class FileSystemEngine[F[_]: Monad, B, K](
  endpoint: Endpoint[F, B, K],
  par: Parallel[F],
  fs: FileSystem[F, B, K]
) {

  import cats.instances.either._
  import cats.instances.vector._
  import cats.syntax.alternative._
  import cats.syntax.bifunctor._
  import cats.syntax.flatMap._
  import cats.syntax.functor._

  private def relativize(blob: B, from: ParsedPath, to: ParsedPath): F[ParsedPath] = {
    @tailrec
    def remainedPaths(blobPaths: List[String], relativeToPaths: List[String]): List[String] =
      (blobPaths, relativeToPaths) match {
        case (remains, Nil)                                           => remains
        case (bpHead :: bpTail, rpHead :: rpTail) if bpHead == rpHead => remainedPaths(bpTail, rpTail)
        case (_ :: bpTail, relative)                                  => remainedPaths(bpTail, relative)
      }

    for {
      blobPath            <- endpoint.blobPath(blob)
      blobPathNames       = blobPath.path.split("/").toList
      relativeToPathNames = from.prefix.path.split("/").toList
      remainedPathNames   = remainedPaths(blobPathNames, relativeToPathNames)
      toPathNames         = to.prefix.path.split("/")
    } yield to.copy(prefix = Prefix((toPathNames ++ remainedPathNames).mkString("/")))
  }

  private def forEachBlobIn[U](path: ParsedPath)(action: B => F[Either[ActionFailed, U]]) =
    (for {
      container <- EitherT.right[Fatal with Aggregate](endpoint.toContainer(path))
      actionResults <- EitherT(fs.foreachBlob(container, path.prefix) { par.traverse(_)(action) })
                        .leftSemiflatMap(e => containerListingFailure(container, path.prefix, e))
                        .leftWiden[Fatal with Aggregate]

      (failed, succeed) = actionResults.toVector.separate
    } yield EvaluationSummary(succeed.size, failed)).value

  private def containerListingFailure(container: K, prefix: Prefix, cause: Throwable) =
    for {
      cont <- endpoint.containerPath(container)
    } yield FileSystemFailure(s"Failed to perform listing in ${cont.path} (${prefix.path})", cause)

  private def copyFailure(from: B, to: B, cause: Throwable) =
    for {
      fromBlobPath <- endpoint.blobPath(from)
      toBlobPath   <- endpoint.blobPath(to)
    } yield ActionFailed(s"Failed to copy content of ${fromBlobPath.path} to ${toBlobPath.path}", cause)

  private def removeFailure(from: B, cause: Throwable) =
    for {
      fromBlobPath <- endpoint.blobPath(from)
    } yield ActionFailed(s"Failed to remove ${fromBlobPath.path}", cause)

  private def copyBlobs(from: ParsedPath, to: ParsedPath) = forEachBlobIn(from) { fromBlob =>
    (for {
      toBlobPath <- EitherT.right[ActionFailed](relativize(fromBlob, from, to))
      toBlob     <- EitherT.right[ActionFailed](endpoint.toBlob(toBlobPath))
      _          <- EitherT(fs.copyContent(fromBlob, toBlob)).leftSemiflatMap(e => copyFailure(fromBlob, toBlob, e))
    } yield ()).value
  }

  private def moveBlobs(from: ParsedPath, to: ParsedPath) = forEachBlobIn(from) { fromBlob =>
    (for {
      toBlobPath <- EitherT.right[ActionFailed](relativize(fromBlob, from, to))
      toBlob     <- EitherT.right[ActionFailed](endpoint.toBlob(toBlobPath))
      _          <- EitherT(fs.copyContent(fromBlob, toBlob)).leftSemiflatMap(e => copyFailure(fromBlob, toBlob, e))
      _          <- EitherT(fs.remove(fromBlob)).leftSemiflatMap(e => removeFailure(fromBlob, e))
    } yield ()).value
  }

  private def removeBlobs(path: ParsedPath) = forEachBlobIn(path) { fromBlob =>
    EitherT(fs.remove(fromBlob))
      .leftSemiflatMap(e => removeFailure(fromBlob, e))
      .value
  }

  private def countBlobs(path: ParsedPath) = forEachBlobIn[Unit](path) { _ =>
    Monad[F].pure {
      val a: Either[ActionFailed, Unit] = Right(())
      a
    }
  }

  private val runFsAction =
    new ActionInterpret[F, ParsedPath, Seq[(OperationDescription, Either[Fatal with Aggregate, EvaluationSummary])]] {
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

        case Count(inPaths) =>
          par.traverse(inPaths) { in =>
            for {
              ops    <- countBlobs(in)
              showIn <- endpoint.showPath(in)
            } yield OperationDescription(s"Count blobs in $showIn") -> ops
          }
      }
    }

  def evaluate(
    expression: Expression[ParsedPath]
  ): F[Either[AggregatedFatal, Map[OperationDescription, EvaluationSummary]]] =
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
