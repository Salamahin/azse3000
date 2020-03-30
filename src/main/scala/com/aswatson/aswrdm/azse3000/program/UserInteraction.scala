package com.aswatson.aswrdm.azse3000.program

import cats.data.EitherT
import cats.{Applicative, Monad}
import com.aswatson.aswrdm.azse3000.expression.ActionInterpret
import com.aswatson.aswrdm.azse3000.shared._

class UserInteraction[F[_]: Monad: Applicative](
  implicit
  prompt: Prompt[F],
  syntax: CommandSyntax[F],
  parse: Parse[F],
  vault: Vault[F]
) {
  import cats.instances.either._
  import cats.instances.vector._
  import cats.instances.tuple._
  import cats.syntax.alternative._
  import cats.syntax.traverse._
  import cats.syntax.functor._

  private def getPaths(expr: Expression) = {
    val collectPaths = new ActionInterpret[F, Seq[Path]] {
      override def run(term: Action) = term match {
        case Copy(from, to) => Monad[F].pure(from :+ to)
        case Move(from, to) => Monad[F].pure(from :+ to)
        case Remove(from)   => Monad[F].pure(from)
      }
    }

    ActionInterpret.interpret(expr)(Applicative[F], collectPaths).map(_.flatten)
  }

  private def parsePaths(paths: Vector[Path]) =
    for {
      maybeParsed <- paths.traverse { p => //fixme apply fproduct on monad
                      Applicative[F].fproduct(parse.toFullPath(p))(_ => p)
                    }

      (parsingFailures, parsed) = maybeParsed.map {
        case (converted, original) => converted.map((original, _))
      }.separate

    } yield Either.cond(
      parsingFailures.isEmpty,
      parsed.toMap,
      AggregatedFatals(parsingFailures)
    )

  private def getCreds(paths: Vector[FullPath]) = {
    for {
      pathsAndSecrets <- paths
                          .groupBy(p => (p.account, p.container))
                          .toVector
                          .traverse {
                            case ((acc, cont), ps) =>
                              Applicative[F].fproduct(vault.credsFor(acc, cont)) { c =>
                                ps.map(_ -> c)
                              }
                          }

      (_, secrets) = pathsAndSecrets.separate
    } yield secrets.flatten.toMap
  }

  def run() =
    for {
      rawCommand         <- EitherT.right[AggregatedFatals](prompt.command)
      desugaredCommand   <- EitherT.right[AggregatedFatals](syntax.desugar(rawCommand))
      parsedExpression   <- EitherT(parse.toExpression(desugaredCommand)).leftMap(c => AggregatedFatals(c :: Nil))
      inputPaths         <- EitherT.right[AggregatedFatals](getPaths(parsedExpression))
      parsedPaths        <- EitherT(parsePaths(inputPaths))
      creds              <- EitherT.right[AggregatedFatals](getCreds(parsedPaths.values.toVector))
    } yield (parsedExpression, parsedPaths, creds)
}