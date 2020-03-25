package com.aswatson.aswrdm.azse3000.shared

trait Prompt[F[_]] {
  def command: F[Command]
}

trait Parse[F[_]] {
  def toExpression(prompted: Command): F[Either[InvalidCommand, Expression[InputPath]]]

  def toFullPath(inputPath: InputPath): F[FullPath]
}

trait Endpoint[F[_], T, K] {
  def toBlob(p: FullPath): F[T]
  def toContainer(p: FullPath): F[K]

  def show(p: FullPath): F[String]
}

trait Parallel[F[_]] {
  def traverse[T, U](items: Seq[T])(action: T => F[U]): F[Seq[U]]
  def traverseN[T, U](items: Seq[T])(action: T => F[U]): F[Seq[U]]
}

trait FileSystem[F[_], T, K] {
  def copyContent(fromBlob: T, toBlob: T): F[Either[FileOperationFailed, Unit]]

  def remove(blob: T): F[Either[FileOperationFailed, Unit]]

  def foreachBlob[U](container: K, prefix: RelativePath)(
    action: Seq[T] => F[Seq[U]]
  ): F[Either[FileSystemFailure, Seq[U]]]
}
