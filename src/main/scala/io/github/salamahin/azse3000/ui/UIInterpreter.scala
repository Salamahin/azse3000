//package io.github.salamahin.azse3000.ui
//
//import java.io.{PrintWriter, StringWriter}
//
//import cats.~>
//import io.github.salamahin.azse3000.shared._
//import org.jline.reader.impl.history.DefaultHistory
//import org.jline.reader.{LineReader, LineReaderBuilder}
//import org.jline.terminal.TerminalBuilder
//import zio.clock.Clock
//import zio.{Ref, UIO, URIO}
//
//class UIInterpreter extends (UIOps ~> URIO[Clock, *]) {
//  private val terminal = TerminalBuilder.builder.build
//
//  private val reader = {
//    val lineReader = LineReaderBuilder.builder
//      .terminal(terminal)
//      .variable(LineReader.HISTORY_FILE, ".azse3000_history")
//      .history(new DefaultHistory())
//      .build
//
//    lineReader.unsetOpt(LineReader.Option.INSERT_TAB)
//    lineReader
//  }
//
//  private def formatProgress(op: Description, progress: Int, completed: Boolean) = {
//    val left  = s"${if (completed) "(complete) " else ""}${op.description}"
//    val right = progress.toString
//
//    val space = Seq.fill(Math.max(2, terminal.getWidth - left.length - right.length))('.').mkString
//
//    s"$left$space$right"
//  }
//
//  private def errorStackTrace(err: Throwable) = {
//    val sw = new StringWriter()
//    err.printStackTrace(new PrintWriter(sw))
//    sw.toString
//  }
//
//  private def formatReport(r: InterpretationReport) = {
//    val summary = r.summary match {
//      case CopySummary(succeed)   => s"Copied $succeed blobs"
//      case MoveSummary(succeed)   => s"Processed $succeed blobs"
//      case RemoveSummary(succeed) => s"Removed $succeed blobs"
//      case CountSummary(count)    => s"Total blobs count is $count"
//      case SizeSummary(bytes)     => f"Total blobs size is $bytes bytes (${bytes / 1024.0 / 1024 / 1024}%.2f GiB)"
//    }
//
//    val problems = r.errors
//      .map(errorStackTrace)
//
//    s"""${r.description.description}: $summary
//       |${problems.mkString("\n\n")}""".stripMargin
//  }
//
//  private val progressState = Ref.make[Seq[(Description, Int, Boolean)]](Seq.empty)
//
//  private def showProgress(state: Seq[(Description, Int, Boolean)]) = {
//    val lines = state
//      .map {
//        case (descr, progress, completed) => formatProgress(descr, progress, completed)
//      }
//      .mkString("\n")
//
//    terminal.writer().println(lines)
//    terminal.writer().flush()
//  }
//
//  override def apply[A](fa: UIOps[A]): URIO[Clock, A] =
//    fa match {
//      case PromptCommand() =>
//        UIO {
//          Command(reader.readLine("> "))
//        }
//
//      case ShowProgress(op, progress, complete) =>
//        for {
//          state     <- progressState
//          nextState <- state.update(_ :+ (op, progress, complete))
//          _ = showProgress(nextState)
//        } yield ()
//
//      case ShowReports(reports) =>
//        UIO {
//          terminal.writer().println(reports.map(formatReport).mkString("\n\n"))
//          terminal.writer().flush()
//        }
//    }
//}
