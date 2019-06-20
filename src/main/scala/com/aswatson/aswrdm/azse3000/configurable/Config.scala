package com.aswatson.aswrdm.azse3000.configurable

final case class Config(batchSize: Int, knownHosts: Map[String, String], knownSecrets: Map[String, Map[String, String]])
