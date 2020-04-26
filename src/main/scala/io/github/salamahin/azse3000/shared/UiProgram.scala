package io.github.salamahin.azse3000.shared

import cats.Monad
import cats.data.{EitherT, OptionT}
import cats.free.Free._

object UiProgram {
  import ExecStrategy._
  import cats.implicits._

  def program[F[_]: Monad](
    implicit ui: UserInterface[F],
    parser: Parser[F],
    configuration: Configuration[F],
    interpretation: Interpretation[F]
  ) =
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



//    _ <- ActionInterpret.interpret2(expr)(new ActionInterpret[F, ParsedPath, EvaluationSummary])

    } yield ()).value
}
