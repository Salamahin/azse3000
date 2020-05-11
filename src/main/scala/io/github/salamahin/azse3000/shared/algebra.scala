package io.github.salamahin.azse3000.shared

import cats.InjectK
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

sealed trait Azure[T, B]
object Azure {

  type LISTING_ATTEMPT[B]        = Either[AzureFailure, ListingPage[B]]
  type COPY_ATTEMPT[B]           = Either[AzureFailure, B]
  type REMOVE_ATTEMPT            = Either[AzureFailure, Unit]
  type COPY_STATUS_CHECK_ATTEMPT = Either[AzureFailure, Boolean]
}
final case class StartListing[B](inPath: Path, secret: Secret)                  extends Azure[LISTING_ATTEMPT[B], B]
final case class ContinueListing[B](prevPage: ListingPage[B])                   extends Azure[ListingPage[B], B]
final case class IsCopied[B](blob: B)                                           extends Azure[COPY_STATUS_CHECK_ATTEMPT, B]
final case class RemoveBlob[B](blob: B)                                         extends Azure[REMOVE_ATTEMPT, B]
final case class SizeOfBlobBytes[B](blob: B)                                    extends Azure[Long, B]
final case class StartCopy[B](src: Path, blob: B, dst: Path, dstSecret: Secret) extends Azure[COPY_ATTEMPT[B], B]

sealed trait Control[T]
final case class DelayCopyStatusCheck() extends Control[Unit]

final case class AzureEngine[F[_], B]()(implicit inj: InjectK[Azure[*, B], F]) {
  def startListing(inPath: Path, secret: Secret)                  = inj(StartListing(inPath, secret))
  def continueListing(tkn: ListingPage[B])                        = inj(ContinueListing(tkn))
  def isCopied(blob: B)                                           = inj(IsCopied(blob))
  def removeBlob(blob: B)                                         = inj(RemoveBlob(blob))
  def sizeOfBlobBytes(blob: B)                                    = inj(SizeOfBlobBytes(blob))
  def startCopy(src: Path, blob: B, dst: Path, dstSecret: Secret) =
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
