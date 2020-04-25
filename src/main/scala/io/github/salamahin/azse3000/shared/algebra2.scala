package io.github.salamahin.azse3000.shared

import cats.InjectK

sealed trait UI[T]
final case class PromptCommand()                                       extends UI[Command]
final case class PromptCreds(acc: Account, cont: Container)            extends UI[Secret]
final case class ShowProgress(op: OperationDescription, progress: Int) extends UI[Unit]

sealed trait Parse2[T]
final case class ParsePath(p: Path)         extends Parse2[ParsedPath]
final case class ParseCommand(cmd: Command) extends Parse2[Expression[ParsedPath]]

sealed trait Config[T]
final case class ReadCreds(acc: Account, cont: Container) extends Config[Option[Secret]]

sealed trait Interpret[T]
final case class CollectPath(expr: Expression[ParsedPath]) extends Interpret[Seq[ParsedPath]]

final case class UserInterface[F[_]](implicit inj: InjectK[UI, F]) {
  def promptCommand: ExecStrategy[F, Command]                             = ExecStrategy(inj(PromptCommand()))
  def promptCreds(acc: Account, cont: Container): ExecStrategy[F, Secret] = ExecStrategy(inj(PromptCreds(acc, cont)))
  def showProgress(op: OperationDescription, progress: Int): ExecStrategy[F, Unit] = ExecStrategy(inj(ShowProgress(op, progress)))
}

final case class Parser[F[_]](implicit inj: InjectK[Parse2, F]) {
  def parsePath(p: Path)         = ExecStrategy(inj(ParsePath(p)))
  def parseCommand(cmd: Command) = ExecStrategy(inj(ParseCommand(cmd)))
}

final case class Configuration[F[_]](implicit inj: InjectK[Config, F]) {
  def readCreds(acc: Account, cont: Container) = ExecStrategy(inj(ReadCreds(acc, cont)))
}

final case class Interpretation[F[_]](implicit inj: InjectK[Interpret, F]) {
  def colletPaths(expr: Expression[ParsedPath]) = ExecStrategy(inj(CollectPath(expr)))
}
