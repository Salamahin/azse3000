package io.github.salamahin.azse3000.shared

import cats.{Applicative, Monad}
import cats.data.{EitherT, OptionT}
import cats.free.Free._
import io.github.salamahin.azse3000.expression.ActionInterpret

object UiProgram {
  import ExecStrategy._
  import cats.implicits._

  def program[F[_]: Monad](
    implicit ui: UserInterface[F],
    parser: Parser[F],
    configuration: Configuration[F],
    interpretation: Interpretation[F],
    azure: AzureEngine[F]
  ) = {
    def move(from: )





    (for {
      cmd   <- EitherT.right[InvalidCommand](ui.promptCommand.seq.asProgramStep)
      expr  <- EitherT(parser.parseCommand(cmd).seq.asProgramStep)
      paths <- EitherT.right[InvalidCommand](interpretation.colletPaths(expr).seq.asProgramStep)

      secrets <- {
        val secrets = paths
          .groupBy(p => (p.account, p.container))
          .keys
          .toVector
          .traverse {
            case id @ (acc, cont) =>
              OptionT
                .liftF(configuration.readCreds(acc, cont).seq)
                .getOrElseF(ui.promptCreds(acc, cont).seq)
                .map(secret => id -> secret)
                .asProgramStep
          }
          .map(_.toMap)

        EitherT.right[InvalidCommand](secrets)
      }

      wtf = ActionInterpret.interpret2(new ActionInterpret[F, ParsedPath, EvaluationSummary] {
        override def run(term: Action[ParsedPath]): F[EvaluationSummary] = term match {
          case Copy(from, to) =>

            from
                .map(
                  f => azure
                    .list(f.account, f.container, f.prefix, secrets((f.account, f.container)))
                    .seq
                    .flatMap(blobs => azure.copy(blobs,))
                )




            from
              .toVector
              .traverse(f => azure.list(f.account, f.container, f.prefix, secrets((f.account, f.container))).seq)

          case Move(from, to) =>
          case Remove(from)   =>
          case Count(in)      =>
        }
      })(expr)

    } yield ()).value
  }
}
