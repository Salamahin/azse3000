package io.github.salamahin.azse3000.shared

import cats.InjectK
import com.microsoft.azure.storage.blob.CloudBlockBlob

sealed trait UI[T]
final case class PromptCommand()                                       extends UI[Command]
final case class PromptCreds(acc: Account, cont: Container)            extends UI[Secret]
final case class ShowProgress(op: OperationDescription, progress: Int) extends UI[Unit]

sealed trait Parse2[T]
final case class ParsePath(p: Path)         extends Parse2[ParsedPath]
final case class ParseCommand(cmd: Command) extends Parse2[Either[InvalidCommand, Expression[ParsedPath]]]

sealed trait Config[T]
final case class ReadCreds(acc: Account, cont: Container) extends Config[Option[Secret]]

sealed trait Interpret[T]
final case class CollectPath(expr: Expression[ParsedPath]) extends Interpret[Seq[ParsedPath]]

final case class ListingSegment()

sealed trait Azure[T]
final case class Relativize(from: ParsedPath, to: ParsedPath, toSecret: Secret, blob: CloudBlockBlob)
    extends Azure[CloudBlockBlob]
final case class ListBlobs(acc: Account, cont: Container, prefix: Prefix, secret: Secret)
    extends Azure[Seq[CloudBlockBlob]]

final case class AzureEngine[F[_]]()(implicit inj: InjectK[Azure, F]) {
//  def copy(blobs: Seq[CloudBlockBlob], to: ParsedPath, secrets: CREDS) = ExecStrategy(inj(Copy(blobs, to, secrets)))
//  def remove(blobs: Seq[CloudBlockBlob])                               = ExecStrategy(inj(Remove(blobs)))
//  def move(blobs: Seq[CloudBlockBlob], to: ParsedPath, secrets: CREDS) = ExecStrategy(inj(Move(blobs, to, secrets)))
//
  def relativize(from: ParsedPath, to: ParsedPath, toSecret: Secret, blob: CloudBlockBlob) =
    ExecStrategy(inj(Relativize(from, to, toSecret, blob)))
  def list(acc: Account, cont: Container, prefix: Prefix, secret: Secret) =
    ExecStrategy(inj(ListBlobs(acc, cont, prefix, secret)))
}

final case class UserInterface[F[_]]()(implicit inj: InjectK[UI, F]) {
  def promptCommand                                         = ExecStrategy(inj(PromptCommand()))
  def promptCreds(acc: Account, cont: Container)            = ExecStrategy(inj(PromptCreds(acc, cont)))
  def showProgress(op: OperationDescription, progress: Int) = ExecStrategy(inj(ShowProgress(op, progress)))
}

final case class Parser[F[_]]()(implicit inj: InjectK[Parse2, F]) {
  def parsePath(p: Path)         = ExecStrategy(inj(ParsePath(p)))
  def parseCommand(cmd: Command) = ExecStrategy(inj(ParseCommand(cmd)))
}

final case class Configuration[F[_]]()(implicit inj: InjectK[Config, F]) {
  def readCreds(acc: Account, cont: Container) = ExecStrategy(inj(ReadCreds(acc, cont)))
}

final case class Interpretation[F[_]]()(implicit inj: InjectK[Interpret, F]) {
  def colletPaths(expr: Expression[ParsedPath]) = ExecStrategy(inj(CollectPath(expr)))
}
