package io.github.salamahin.azse3000

import java.io.File

import org.jline.reader.impl.history.DefaultHistory
import org.jline.reader.{LineReader, LineReaderBuilder}
import org.jline.terminal.{Terminal, TerminalBuilder}

object Test extends App {
//  System.setProperty("org.jline.terminal.dumb", "true")
//  System.setProperty("jansi.passthrough", "true")

  val terminal: Terminal =
    TerminalBuilder
      .builder
//        .jansi(true)
      .build

  val historyFile = new File(".azse3000_history")

  def reader(terminal: Terminal): LineReader = {
    val lineReader: LineReader = LineReaderBuilder
      .builder
      .terminal(terminal)
      //      .parser(parser)
      .variable(LineReader.HISTORY_FILE, historyFile)
      .history(new DefaultHistory())
      .appName("hui")
      .build

//    lineReader.unsetOpt(LineReader.Option.INSERT_TAB)
    lineReader
  }

  reader(terminal).readLine("> ")
}
