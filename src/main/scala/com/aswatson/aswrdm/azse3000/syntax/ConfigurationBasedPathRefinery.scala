package com.aswatson.aswrdm.azse3000.syntax

import com.aswatson.aswrdm.azse3000.shared.Command

object ConfigurationBasedPathRefinery {
  private val pattern = "([\\w-]+)@([\\w-]+):/([\\w-./=]+)?".r

  def refinePaths(knownHosts: Map[String, String])(command: Command) = {
    val refinedCommand = pattern.replaceAllIn(
      command.cmd,
      x => {
        val container = x.group(1)
        val account   = x.group(2)
        val p         = x.group(3)

        knownHosts
          .get(account)
          .map(host => s"$host/$container/$p")
          .getOrElse(x.matched)
      }
    )

    Command(refinedCommand)
  }
}
