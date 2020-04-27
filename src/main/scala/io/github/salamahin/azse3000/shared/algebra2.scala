package io.github.salamahin.azse3000.shared

import cats.InjectK
import com.microsoft.azure.storage.ResultSegment
import com.microsoft.azure.storage.blob.{CloudBlockBlob, ListBlobItem}

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

sealed trait Azure[T]
final case class Relativize(from: ParsedPath, fromBlob: CloudBlockBlob, to: ParsedPath, toSecret: Secret)
    extends Azure[Either[FileSystemFailure, CloudBlockBlob]]
final case class StartListing(acc: Account, cont: Container, prefix: Prefix, secret: Secret)
    extends Azure[Either[FileSystemFailure, ResultSegment[ListBlobItem]]]


sealed trait Control[T]
final case class DelayCopyStatusCheck() extends Control[Unit]

final case class AzureEngine[F[_]]()(implicit inj: InjectK[Azure, F]) {
  def relativize(from: ParsedPath, fromBlob: CloudBlockBlob, to: ParsedPath, toSecret: Secret) =
    ExecStrategy(inj(Relativize(from, fromBlob, to, toSecret)))

  def startListing(acc: Account, cont: Container, prefix: Prefix, secret: Secret) =
    ExecStrategy(inj(StartListing(acc, cont, prefix, secret)))
}

final case class ConcurrentController[F[_]]()(implicit inj: InjectK[Control, F]) {
  def delayCopyStatusCheck() = ExecStrategy(inj(DelayCopyStatusCheck()))
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