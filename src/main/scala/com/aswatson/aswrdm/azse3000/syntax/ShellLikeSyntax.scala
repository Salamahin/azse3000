package com.aswatson.aswrdm.azse3000.syntax

import cats.Monad
import com.aswatson.aswrdm.azse3000.shared.{Command, CommandSyntax}

class ShellLikeSyntax[F[_]: Monad](knownHosts: Map[String, String]) extends CommandSyntax[F] {
  override def desugar(cmd: Command) = Monad[F].pure {
    ExpandCurlyBraces.expand(
      ToUrlFormat.refine(knownHosts)(cmd)
    )
  }
}
