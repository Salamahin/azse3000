package io.github.salamahin.azse3000.syntax

import cats.Monad
import io.github.salamahin.azse3000.shared.{Command, CommandSyntax}

class ShellLikeSyntax[F[_]: Monad](knownHosts: Map[String, String]) extends CommandSyntax[F] {
  override def desugar(cmd: Command) = Monad[F].pure {
    ToUrlFormat.refine(knownHosts)(ExpandCurlyBraces.expand(cmd))
  }
}
