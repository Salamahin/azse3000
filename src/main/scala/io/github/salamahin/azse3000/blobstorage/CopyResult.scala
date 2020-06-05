package io.github.salamahin.azse3000.blobstorage
import io.github.salamahin.azse3000.shared.AzureFailure

final case class CopyResult[B](copied: Vector[B], errors: Vector[AzureFailure])
