package com.aswatson.aswrdm.azse3000.shared

import com.aswatson.aswrdm.azse3000.shared.types.DECOMPOSED_PATH

trait Prompt[F[_]] {
  def command: F[Command]

  def creds(acc: Account, cont: ContainerName): F[Secret]
}

trait Parse[F[_]] {
  def toExpression(prompted: Command): F[Either[InvalidCommand, Expression]]
}

trait Preprocess[F[_]] {
  def rebuild(command: Command): F[Command]
}

trait CredsRepo[F[_]] {
  def creds(acc: Account, cont: ContainerName): F[Option[Secret]]
}

trait Watcher[F[_]] {
  def lookAfter[T](program: F[T]): F[T]
}

trait EndpointUri[F[_], T, K] {
  def decompose(path: Path): F[Either[MalformedPath, DECOMPOSED_PATH]]

  def pathWithinContainer(file: T): F[Path]

  def toFile(secret: Secret, fullPath: Path): F[T]

  def findContainer(secret: Secret, excessPath: Path): F[Either[NoSuchContainer, K]]
}

trait Parallel[F[_]] {
  def traverse[T, U](items: Seq[T])(action: T => F[U]): F[Seq[U]]
  def traverseN[T, U](items: Seq[T])(action: T => F[U]): F[Seq[U]]
}

trait FileSystem[F[_], T, K] {
  def copyContent(file: T, to: T): F[Either[FileOperationFailed, Unit]]

  def remove(file: T): F[Either[FileOperationFailed, Unit]]

  def foreachFile[U](container: K, prefix: Path)(action: Seq[T] => F[Seq[U]]): F[Either[FileSystemFailure, Seq[U]]]
}
