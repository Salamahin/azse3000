package io.github.salamahin.azse3000
import cats.data.EitherK
import io.github.salamahin.azse3000.delay.{DelayOps, Delays}
import io.github.salamahin.azse3000.parsing.{Parser, ParsingOps}
import io.github.salamahin.azse3000.ui.{UIOps, UserInterface}

object TestProgram {

  type Application[A] = EitherK[UIOps, EitherK[DelayOps, ParsingOps, *], A]

  import ProgramSyntax._

  def apply()(implicit UI: UserInterface[Application], D: Delays[Application], P: Parser[Application]) = {
    import D._
    import P._
    import UI._

    for {
      cmd <- promptCommand().liftFree
      _   <- parseCommand(cmd).liftFree
      _   <- delayCopyStatusCheck().liftFree
    } yield ()
  }

}
