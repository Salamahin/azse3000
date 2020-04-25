package io.github.salamahin.azse3000.shared

import cats.Monad
import cats.free.Free
import cats.free.Free._

object UiProgram {
  import ExecStrategy._
  import cats.implicits._

  def program[F[_]: Monad](
    implicit ui: UserInterface[F],
    parser: Parser[F],
    configuration: Configuration[F],
    interpretation: Interpretation[F]
  ): Program[F, Unit] =
    for {
      cmd   <- ui.promptCommand.seq.asProgramStep
      expr  <- parser.parseCommand(cmd).seq.asProgramStep
      paths <- interpretation.colletPaths(expr).seq.asProgramStep

      secrets <- paths
                  .groupBy(p => (p.account, p.container))
                  .toVector
                  .traverse {
                    case ((acc, cont), paths) =>
                      configuration
                        .readCreds(acc, cont)
                        .seq
                        .flatMap(
                          maybeCreds =>
                            maybeCreds
                              .map(aaa => Free.pure[F, Secret](aaa))
                              .getOrElse(ui.promptCreds(acc, cont).seq)
                        )
                        .map { secret =>
                          paths.map(_ -> secret)
                        }
                        .asProgramStep
                  }
                  .map(_.flatten.toMap)

    } yield ()
}
