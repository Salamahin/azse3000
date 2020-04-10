package com.aswatson.aswrdm.azse3000.syntax

import com.aswatson.aswrdm.azse3000.shared.Command

import scala.annotation.tailrec

object ExpandCurlyBraces {
  private def pattern = "([^\\{ ]+)?\\{([^\\} ]+),?\\}([^ ]+)?".r

  @tailrec
  def expand(command: Command): Command = {
    val refined = Command(
      pattern.replaceAllIn(
        command.cmd,
        x => {
          val prefix  = Option(x.group(1)).getOrElse("")
          val body    = x.group(2).split(",")
          val postfix = Option(x.group(3)).getOrElse("")

          body.map(b => s"$prefix$b$postfix").mkString(" ")
        }
      )
    )

    if (refined == command) refined
    else expand(refined)
  }
}
