package io.github.salamahin.azse3000.interpret
import cats.~>
import io.github.salamahin.azse3000.shared._
import org.jline.reader.impl.history.DefaultHistory
import org.jline.reader.{LineReader, LineReaderBuilder}
import org.jline.terminal.TerminalBuilder
import zio.{Ref, UIO}

class UIInterpreter extends (UI ~> UIO) {
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

  private val progressState = Ref.make[Map[Description, (Int, Boolean)]](Map.empty)

  override def apply[A](fa: UI[A]): UIO[A] =
    fa match {
      case PromptCommand()        => UIO { Command(reader.readLine("> ")) }
      case PromptCreds(acc, cont) => UIO { Secret(reader.readLine(s"SAS for ${acc.name}@${cont.name}: ", '*')) }
      case ShowProgress(op, progress, complete) =>
        progressState
          .flatMap { state =>
            state.update { m =>
              m + (op -> (progress, complete))
            }
          } *> UIO.unit
    }
}
