package io.github.salamahin.azse3000.shared

import cats.InjectK
import com.microsoft.azure.storage.blob.CloudBlockBlob
import io.github.salamahin.azse3000.shared.BlobStorageAPI.{COPY_ATTEMPT, COPY_STATUS_CHECK_ATTEMPT, LISTING_ATTEMPT, REMOVE_ATTEMPT}

sealed trait UI[T]
final case class PromptCommand()                                                 extends UI[Command]
final case class PromptCreds(acc: Account, cont: Container)                      extends UI[Secret]
final case class ShowProgress(op: Description, progress: Int, complete: Boolean) extends UI[Unit]
final case class ShowReports(reports: Vector[InterpretationReport])              extends UI[Unit]

sealed trait CommandParsing[T]
final case class ParseCommand(cmd: Command) extends CommandParsing[Either[MalformedCommand, Expression]]

sealed trait Vault[T]
final case class ReadCreds(acc: Account, cont: Container) extends Vault[Option[Secret]]

sealed trait BlobStorageAPI[T]
object BlobStorageAPI {
  type LISTING_ATTEMPT           = Either[AzureFailure, ListingPage]
  type COPY_ATTEMPT              = Either[AzureFailure, CloudBlockBlob]
  type REMOVE_ATTEMPT            = Either[AzureFailure, Unit]
  type COPY_STATUS_CHECK_ATTEMPT = Either[AzureFailure, Boolean]
}
final case class StartListing(inPath: Path, secret: Secret)                               extends BlobStorageAPI[LISTING_ATTEMPT]
final case class StartCopy(src: Path, blob: CloudBlockBlob, dst: Path, dstSecret: Secret) extends BlobStorageAPI[COPY_ATTEMPT]
final case class ContinueListing(prevPage: ListingPage)                                   extends BlobStorageAPI[Option[ListingPage]]
final case class IsCopied(blob: CloudBlockBlob)                                           extends BlobStorageAPI[COPY_STATUS_CHECK_ATTEMPT]
final case class RemoveBlob(blob: CloudBlockBlob)                                         extends BlobStorageAPI[REMOVE_ATTEMPT]
final case class SizeOfBlobBytes(blob: CloudBlockBlob)                                    extends BlobStorageAPI[Long]

sealed trait Delay[T]
final case class DelayCopyStatusCheck() extends Delay[Unit]

final case class BlobStorage[F[_]]()(implicit inj: InjectK[BlobStorageAPI, F]) {
  def startListing(inPath: Path, secret: Secret)                               = inj(StartListing(inPath, secret))
  def continueListing(tkn: ListingPage)                                        = inj(ContinueListing(tkn))
  def isCopied(blob: CloudBlockBlob)                                           = inj(IsCopied(blob))
  def removeBlob(blob: CloudBlockBlob)                                         = inj(RemoveBlob(blob))
  def sizeOfBlobBytes(blob: CloudBlockBlob)                                    = inj(SizeOfBlobBytes(blob))
  def startCopy(src: Path, blob: CloudBlockBlob, dst: Path, dstSecret: Secret) = inj(StartCopy(src, blob, dst, dstSecret))
}

final case class Delays[F[_]]()(implicit inj: InjectK[Delay, F]) {
  def delayCopyStatusCheck() = inj(DelayCopyStatusCheck())
}

final case class UserInterface[F[_]]()(implicit inj: InjectK[UI, F]) {
  def promptCommand()                                                 = inj(PromptCommand())
  def promptCreds(acc: Account, cont: Container)                      = inj(PromptCreds(acc, cont))
  def showProgress(op: Description, progress: Int, complete: Boolean) = inj(ShowProgress(op, progress, complete))
  def showReports(reports: Vector[InterpretationReport])              = inj(ShowReports(reports))
}

final case class Parser[F[_]]()(implicit inj: InjectK[CommandParsing, F]) {
  def parseCommand(cmd: Command) = inj(ParseCommand(cmd))
}

final case class VaultStorage[F[_]]()(implicit inj: InjectK[Vault, F]) {
  def readCreds(acc: Account, cont: Container) = inj(ReadCreds(acc, cont))
}
