package com.aswatson.aswrdm.azse3000.syntax

import java.net.URLEncoder

import com.aswatson.aswrdm.azse3000.shared.Command

object EncodeUrl {
  def encode(command: Command): Command = Command(URLEncoder.encode(command.cmd, "UTF-8"))
}
