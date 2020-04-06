package com.aswatson.aswrdm.azse3000.syntax

import com.aswatson.aswrdm.azse3000.shared.Command

object ExpandCurlyBraces {
  private def pattern = "([^ ]+)?\\{([^ ]+),?\\}([^ ]+)?".r

  def expand(command: Command): Command = {
    val refinedCommand = pattern.replaceAllIn(
      command.cmd,
      x => {
        val prefix  = Option(x.group(1)).getOrElse("")
        val body    = x.group(2).split(",")
        val postfix = Option(x.group(3)).getOrElse("")

        body.map(b => s"$prefix$b$postfix").mkString(" ")
      }
    )

    Command(refinedCommand)
  }
}
