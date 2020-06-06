package io.github.salamahin.azse3000.blobstorage
import cats.InjectK
import io.github.salamahin.azse3000.shared._

sealed trait BlobStorageOps[T]
final case class CopyBlobs(from: Path, to: Path) extends BlobStorageOps[CopySummary]
final case class MoveBlobs(from: Path, to: Path) extends BlobStorageOps[MoveSummary]
final case class RemoveBlobs(from: Path)         extends BlobStorageOps[RemoveSummary]
final case class CountBlobs(in: Path)            extends BlobStorageOps[CountSummary]
final case class SizeOfBlobs(in: Path)           extends BlobStorageOps[SizeSummary]

final class BlobStorage[F[_]]()(implicit inj: InjectK[BlobStorageOps, F]) {
  def copyBlobs(from: Path, to: Path) = inj(CopyBlobs(from, to))
  def moveBlobs(from: Path, to: Path) = inj(MoveBlobs(from, to))
  def removeBlobs(from: Path)         = inj(RemoveBlobs(from))
  def countBlobs(in: Path)            = inj(CountBlobs(in))
  def sizeOfBlobs(in: Path)           = inj(SizeOfBlobs(in))
}

object BlobStorage {
  implicit def blobStorage[F[_]](implicit inj: InjectK[BlobStorageOps, F]): BlobStorage[F] = new BlobStorage[F]
}
