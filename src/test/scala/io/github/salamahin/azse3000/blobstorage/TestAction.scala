package io.github.salamahin.azse3000.blobstorage

sealed trait TestAction[T]
final case class LogBlobOperation(blob: Blob) extends TestAction[Unit]
