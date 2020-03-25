package com.aswatson.aswrdm.azse3000.shared

sealed trait Issue
sealed trait Aggregate
final case class InvalidCommand(msg: String)                         extends Exception with Issue with Aggregate
final case class MalformedPath(path: FullPath)                       extends Exception with Issue with Aggregate
final case class NoSuchContainer(path: FullPath)                     extends Exception with Issue with Aggregate
final case class FileSystemFailure(path: FullPath, cause: Throwable) extends Exception with Issue with Aggregate
final case class Failure(reasons: Seq[Issue with Aggregate])         extends Exception with Issue

final case class InputPath(path: String)    extends AnyVal
final case class RelativePath(path: String) extends AnyVal
final case class FullPath(account: Account, container: Container, relative: RelativePath) {
  def resolve(other: RelativePath) = copy(relative = RelativePath(s"${relative.path}/${other.path}"))
}

final case class Command(cmd: String)                      extends AnyVal
final case class Account(name: String)                     extends AnyVal
final case class Secret(secret: String)                    extends AnyVal
final case class Container(name: String)                   extends AnyVal
final case class OperationDescription(description: String) extends AnyVal

sealed trait Expression[T]
sealed trait Action[T]
final case class And[T](left: Expression[T], right: Expression[T]) extends Expression[T]
final case class Copy[T](from: Seq[T], to: T)                      extends Expression[T] with Action[T]
final case class Move[T](from: Seq[T], to: T)                      extends Expression[T] with Action[T]
final case class Remove[T](from: Seq[T])                           extends Expression[T] with Action[T]

final case class FileOperationFailed(file: FullPath, th: Throwable)
final case class OperationResult(succeed: Long, errors: Vector[FileOperationFailed])

object types {
  type OPERATIONS_SUMMARY = Map[OperationDescription, OperationResult]
}
