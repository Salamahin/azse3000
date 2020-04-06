package com.aswatson.aswrdm.azse3000.configurable

sealed trait ListingMode
case class FlatLimited(maxFetchBlobs: Int) extends ListingMode
case object Recursive   extends ListingMode

final case class Config(
  parallelism: Int,
  listingMode: ListingMode,
  knownHosts: Map[String, String],
  knownSecrets: Map[String, Map[String, String]]
)
