package io.github.salamahin.azse3000

import java.io.File

import org.jline.reader.impl.history.DefaultHistory
import org.jline.reader.{LineReader, LineReaderBuilder}
import org.jline.terminal.{Terminal, TerminalBuilder}

object Test extends App {

  val terminal: Terminal =
    TerminalBuilder
      .builder
      .build

  val historyFile = new File(".azse3000_history")

  val reader = LineReaderBuilder
    .builder
    .terminal(terminal)
    .variable(LineReader.HISTORY_FILE, historyFile)
    .history(new DefaultHistory())
    .appName("hui")
    .build

  new Thread(() => {

    while(!Thread.interrupted()) {
      Thread.sleep(1000)
      reader.callWidget(LineReader.CLEAR)
      terminal.writer().println( "test" );
      reader.callWidget(LineReader.REDRAW_LINE)
      reader.callWidget(LineReader.REDISPLAY)
    }

  })
    .start()

  reader.readLine("> ")

}
