package io.github.salamahin.azse3000.shared

import cats.InjectK
import com.microsoft.azure.storage.blob.{CloudBlockBlob, ListBlobItem}
import com.microsoft.azure.storage.{ResultContinuation, ResultSegment}
import io.github.salamahin.azse3000.shared.Azure.{
  COPY_ATTEMPT,
  COPY_STATUS_CHECK_ATTEMPT,
  LISTING_ATTEMPT,
  REMOVE_ATTEMPT
}

sealed trait UI[T]
final case class PromptCommand()                              extends UI[Command]
final case class PromptCreds(acc: Account, cont: Container)   extends UI[Secret]
final case class ShowProgress(op: Description, progress: Int) extends UI[Unit]

sealed trait Parse[T]
final case class ParseCommand(cmd: Command) extends Parse[Either[MalformedCommand, Expression]]

sealed trait Config[T]
final case class ReadCreds(acc: Account, cont: Container) extends Config[Option[Secret]]

sealed trait Interpret[T]
final case class CollectPath(expr: Expression) extends Interpret[Seq[Path]]

sealed trait Azure[T]
object Azure                                                          {
  type LISTING_ATTEMPT           = Either[AzureFailure, ResultSegment[ListBlobItem]]
  type COPY_ATTEMPT              = Either[AzureFailure, CloudBlockBlob]
  type REMOVE_ATTEMPT            = Either[AzureFailure, Unit]
  type COPY_STATUS_CHECK_ATTEMPT = Either[AzureFailure, Boolean]
}
final case class StartListing(inPath: Path, secret: Secret)                               extends Azure[LISTING_ATTEMPT]
final case class ContinueListing(tkn: ResultContinuation)                                 extends Azure[ResultSegment[ListBlobItem]]
final case class IsCopied(blob: CloudBlockBlob)                                           extends Azure[COPY_STATUS_CHECK_ATTEMPT]
final case class RemoveBlob(blob: CloudBlockBlob)                                         extends Azure[REMOVE_ATTEMPT]
final case class StartCopy(src: Path, blob: CloudBlockBlob, dst: Path, dstSecret: Secret) extends Azure[COPY_ATTEMPT]

sealed trait Control[T]
final case class DelayCopyStatusCheck() extends Control[Unit]

final case class AzureEngine[F[_]]()(implicit inj: InjectK[Azure, F]) {
  def startListing(inPath: Path, secret: Secret)                               = inj(StartListing(inPath, secret))
  def continueListing(tkn: ResultContinuation)                                 = inj(ContinueListing(tkn))
  def isCopied(blob: CloudBlockBlob)                                           = inj(IsCopied(blob))
  def removeBlob(blob: CloudBlockBlob)                                         = inj(RemoveBlob(blob))
  def startCopy(src: Path, blob: CloudBlockBlob, dst: Path, dstSecret: Secret) =
    inj(StartCopy(src, blob, dst, dstSecret))
}

final case class ConcurrentController[F[_]]()(implicit inj: InjectK[Control, F]) {
  def delayCopyStatusCheck() = inj(DelayCopyStatusCheck())
}

final case class UserInterface[F[_]]()(implicit inj: InjectK[UI, F]) {
  def promptCommand()                              = inj(PromptCommand())
  def promptCreds(acc: Account, cont: Container)   = inj(PromptCreds(acc, cont))
  def showProgress(op: Description, progress: Int) = inj(ShowProgress(op, progress))
}

final case class Parser[F[_]]()(implicit inj: InjectK[Parse, F]) {
  def parseCommand(cmd: Command) = inj(ParseCommand(cmd))
}

final case class Configuration[F[_]]()(implicit inj: InjectK[Config, F]) {
  def readCreds(acc: Account, cont: Container) = inj(ReadCreds(acc, cont))
}
