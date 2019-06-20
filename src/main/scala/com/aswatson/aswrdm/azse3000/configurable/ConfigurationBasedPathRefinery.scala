package com.aswatson.aswrdm.azse3000.configurable

import com.aswatson.aswrdm.azse3000.shared.Path

object ConfigurationBasedPathRefinery {
  private val pattern = "([\\w-]+)@([\\w-]+):/([\\w./=]+)".r

  def refine(knownHosts: Map[String, String])(path: Path) = path.path match {
    case pattern(container, account, p) =>
      knownHosts
        .get(account)
        .map(host => Path(s"$host/$container/$p"))
        .getOrElse(path)

    case _ => path
  }
}
