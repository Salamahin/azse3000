package io.github.salamahin.azse3000.ui

import cats.~>
import io.github.salamahin.azse3000.shared._
import org.jline.reader.impl.history.DefaultHistory
import org.jline.reader.{LineReader, LineReaderBuilder}
import org.jline.terminal.TerminalBuilder
import org.jline.utils.InfoCmp.Capability
import zio.clock.Clock
import zio.{Ref, UIO, URIO}

class UIInterpreter extends (UIOps ~> URIO[Clock, *]) {
  private val terminal = TerminalBuilder.builder.build

  private val reader = {
    val lineReader = LineReaderBuilder.builder
      .terminal(terminal)
      .variable(LineReader.HISTORY_FILE, ".azse3000_history")
      .history(new DefaultHistory())
      .build

    lineReader.unsetOpt(LineReader.Option.INSERT_TAB)
    lineReader
  }

  private def formatProgress(op: Description, progress: Int, completed: Boolean) = {
    val left  = s"${if (completed) "(complete) " else ""}${op.description}"
    val right = progress.toString

    val space = Seq.fill(Math.max(2, terminal.getWidth - left.length - right.length))(".")

    s"$left$space$right"
  }

  private val progressState = Ref.make[Seq[(Description, Int, Boolean)]](Seq.empty)

  private def showProgress(state: Seq[(Description, Int, Boolean)]) = {
    val lines = state
      .map {
        case (descr, progress, completed) => formatProgress(descr, progress, completed)
      }
      .mkString("\n")

    terminal.puts(Capability.clear_screen)
    terminal.writer().print(lines)
  }

  override def apply[A](fa: UIOps[A]): URIO[Clock, A] =
    fa match {
      case PromptCommand() => UIO { Command(reader.readLine("> ")) }

      case PromptCreds(acc, cont) => UIO { Secret(reader.readLine(s"SAS for ${acc.name}@${cont.name}: ", '*')) }

      case ShowProgress(op, progress, complete) => ???
//        for {
//          state     <- progressState
//          nextState <- state.update(_ :+ (op, progress, complete))
//          _ = showProgress(nextState)
//        } yield ()

      case ShowReports(reports) => ???
    }
}
