package com.aswatson.aswrdm.azse3000.shared

trait Prompt[F[_]] {
  def command: F[Command]
}

trait Parse[F[_]] {
  def toExpression(prompted: Command): F[Either[InvalidCommand, Expression[Path]]]
  def toFullPath(inputPath: Path): F[Either[MalformedPath, ParsedPath]]
}

trait CommandSyntax[F[_]] {
  def desugar(cmd: Command): F[Command]
}

trait Vault[F[_]] {
  def credsFor(acc: Account, cont: Container): F[Secret]
}

trait Endpoint[F[_], B, K] {
  def toBlob(p: ParsedPath): F[B]
  def toContainer(p: ParsedPath): F[K]

  def showBlob(p: B): F[String]
  def showContainer(p: K): F[String]
  def showPath(p: ParsedPath): F[String]
}

trait Parallel[F[_]] {
  def traverse[T, U](items: Seq[T])(action: T => F[U]): F[Seq[U]]
  def zip[T, U](first: F[T], second: F[U]): F[(T, U)]
}

trait FileSystem[F[_], T, K] {
  def copyContent(fromBlob: T, toBlob: T): F[Either[OperationFailure, Unit]]

  def remove(blob: T): F[Either[OperationFailure, Unit]]

  def foreachBlob[U](container: K, prefix: RelativePath)(
    action: Seq[T] => F[Seq[U]]
  ): F[Either[FileSystemFailure, Seq[U]]]
}
