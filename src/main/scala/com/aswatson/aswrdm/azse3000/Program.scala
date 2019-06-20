package com.aswatson.aswrdm.azse3000

import cats.data.{EitherT, OptionT}
import cats.{Applicative, Monad}
import com.aswatson.aswrdm.azse3000.expression.ActionInterpret
import com.aswatson.aswrdm.azse3000.shared._
import com.aswatson.aswrdm.azse3000.shared.types.{CREDS, REQUIRED_CREDS_FOR_PATHS}

class Program[F[_]: Monad: Applicative, T, K](
  implicit
  prompt: Prompt[F],
  credsRepo: CredsRepo[F],
  parse: Parse[F],
  refine: Refine[F],
  endpoint: EndpointUri[F, T, K],
  fs: FileSystem[F, T, K]
) {

  import cats.instances.either._
  import cats.instances.vector._
  import cats.syntax.alternative._
  import cats.syntax.bifunctor._
  import cats.syntax.flatMap._
  import cats.syntax.functor._
  import cats.syntax.traverse._

  private def forEachFileIn[U](creds: CREDS, path: Path)(action: T => F[Either[FileOperationFailed, U]]) =
    (for {
      container            <- EitherT(endpoint.findContainer(creds(path), path)).leftWiden[Issue with Aggregate]
      (_, _, searchPrefix) <- EitherT(endpoint.decompose(path)).leftWiden[Issue with Aggregate]

      results <- EitherT.right[Issue with Aggregate](for {
                  actionResults     <- fs.foreachFile(container, searchPrefix) { fs.traverse(_)(action) }
                  (failed, succeed) = actionResults.toVector.separate
                } yield OperationResult(succeed.size, failed))

    } yield results).value

  private def copyFiles(creds: CREDS, from: Path, to: Path) = forEachFileIn(creds, from) { fromFile =>
    (for {
      filePrefix <- EitherT.right[FileOperationFailed](endpoint.pathWithinContainer(fromFile))
      toFile     <- EitherT.right[FileOperationFailed](endpoint.toFile(creds(to), to.resolve(filePrefix)))
      _          <- EitherT(fs.copyContent(fromFile, toFile))
    } yield ()).value
  }

  private def moveFiles(creds: CREDS, from: Path, to: Path) = forEachFileIn(creds, from) { fromFile =>
    (for {
      filePrefix <- EitherT.right[FileOperationFailed](endpoint.pathWithinContainer(fromFile))
      toFile     <- EitherT.right[FileOperationFailed](endpoint.toFile(creds(to), to.resolve(filePrefix)))
      _          <- EitherT(fs.copyContent(fromFile, toFile))
      _          <- EitherT(fs.remove(toFile))
    } yield ()).value
  }

  private def removeFiles(creds: CREDS, from: Path) =
    forEachFileIn(creds, from)(
      f =>
        for {
          result <- fs.remove(f)
        } yield result
    )

  private def refinePaths(expr: Expression): F[Expression] = {
    expr match {
      case And(left, right) =>
        for {
          refinedLeft  <- refinePaths(left)
          refinedRight <- refinePaths(right)
        } yield And(refinedLeft, refinedRight)

      case Copy(from, to) =>
        for {
          refinedFrom <- refine.path(from)
          refinedTo   <- refine.path(to)
        } yield Copy(refinedFrom, refinedTo)

      case Move(from, to) =>
        for {
          refinedFrom <- refine.path(from)
          refinedTo   <- refine.path(to)
        } yield Move(refinedFrom, refinedTo)

      case Remove(from) =>
        for {
          refinedSources <- refine.path(from)
        } yield Remove(refinedSources)
    }
  }

  private def getCreds(tree: Expression) = {

    (for {
      collectedPaths <- EitherT.right[Failure](collectPaths(tree))
      maybeEndpointInfos <- EitherT.right[Failure](
                             collectedPaths.traverse(p => Applicative[F].fproduct(endpoint.decompose(p))(_ => p))
                           )

      (problematicPaths, validPaths) = maybeEndpointInfos.map {
        case (maybeInfo, path) => maybeInfo.map(_ -> path)
      }.separate

      secretsToGet <- EitherT
                       .cond[F](
                         problematicPaths.isEmpty,
                         validPaths
                           .groupBy {
                             case ((acc, cont, _), _) => (acc, cont)
                           }
                           .map {
                             case (contAndAcc, elems) => contAndAcc -> elems.map(_._2)
                           },
                         Failure(problematicPaths)
                       )

      secretsForPaths <- EitherT.right[Failure](getCredsForPaths(secretsToGet))

      creds = secretsForPaths.flatMap { case (s, ps) => ps.map(_ -> s) }.toMap

    } yield creds).value
  }

  private def getCredsForPaths(secretsToGet: REQUIRED_CREDS_FOR_PATHS) = {
    secretsToGet
      .toVector
      .traverse {
        case ((acc, cont), paths) =>
          Applicative[F].fproduct {
            OptionT(credsRepo.creds(acc, cont)).getOrElseF(prompt.creds(acc, cont))
          }(_ => paths)
      }
  }

  private def collectPaths(tree: Expression) = {
    val collectPathInterpret = new ActionInterpret[F, Seq[Path]] {
      override def run(term: Action): F[Seq[Path]] =
        term match {
          case Copy(from, to) => Monad[F].pure(Seq(from, to))
          case Move(from, to) => Monad[F].pure(Seq(from, to))
          case Remove(from)   => Monad[F].pure(Seq(from))
        }
    }

    ActionInterpret
      .interpret(tree)(Applicative[F], collectPathInterpret)
      .map(_.flatten)
      .map(_.distinct)
  }

  private def runActions(expression: Expression, creds: CREDS) = {
    val action = new ActionInterpret[F, (OperationDescription, Either[Issue with Aggregate, OperationResult])] {
      override def run(term: Action) = term match {

        case Copy(from, to) =>
          for {
            ops <- copyFiles(creds, from, to)
          } yield OperationDescription(s"Copy from ${from.path} to ${to.path}") -> ops

        case Move(from, to) =>
          for {
            ops <- moveFiles(creds, from, to)
          } yield OperationDescription(s"Move from ${from.path} to ${to.path}") -> ops

        case Remove(from) =>
          for {
            ops <- removeFiles(creds, from)
          } yield OperationDescription(s"Remove from ${from.path}") -> ops
      }
    }

    for {
      interpreted <- ActionInterpret.interpret(expression)(Applicative[F], action)
      (failures, suceeds) = interpreted.map {
        case (descr, opsResults) => opsResults.map(descr -> _)
      }.separate
    } yield if (failures.nonEmpty) Left(Failure(failures)) else Right(suceeds.toMap)
  }

  def run = {
    (for {
      command              <- EitherT.right[Failure](prompt.command)
      expression           <- EitherT(parse.toExpression(command)).leftMap(x => Failure(Seq(x)))
      exprWithPathsRefined <- EitherT.right[Failure](refinePaths(expression))
      creds                <- EitherT(getCreds(exprWithPathsRefined))
      summary              <- EitherT(runActions(exprWithPathsRefined, creds))
    } yield summary).value
  }
}
