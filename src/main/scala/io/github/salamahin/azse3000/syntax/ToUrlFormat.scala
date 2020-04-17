package io.github.salamahin.azse3000.syntax

import io.github.salamahin.azse3000.shared.Command

object ToUrlFormat {
  private val pattern = "([\\w-]+)@([\\w-]+):/([\\w-./=]+)?".r

  def refine(knownHosts: Map[String, String])(command: Command) = {
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
