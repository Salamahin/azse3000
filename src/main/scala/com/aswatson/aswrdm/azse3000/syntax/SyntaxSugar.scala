package com.aswatson.aswrdm.azse3000.syntax

import com.aswatson.aswrdm.azse3000.shared.Command

class SyntaxSugar(knownHosts: Map[String, String]) {
  def desugar(cmd: Command) = EncodeUrl.encode(
    ExpandCurlyBraces.expand(
      ToUrlFormat.refine(knownHosts)(cmd)
    )
  )
}
