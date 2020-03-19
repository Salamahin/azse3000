package com.aswatson.aswrdm.azse3000.shared

sealed trait Issue
sealed trait Aggregate
final case class InvalidCommand(msg: String)                 extends Exception with Issue with Aggregate
final case class MalformedPath(path: Path)                   extends Exception with Issue with Aggregate
final case class NoSuchContainer(path: Path)                 extends Exception with Issue with Aggregate
final case class Failure(reasons: Seq[Issue with Aggregate]) extends Exception with Issue

final case class Path(path: String) extends AnyVal {
  def resolve(other: Path): Path = Path(s"$path/${other.path}")
}
final case class Command(cmd: String)                      extends AnyVal
final case class Account(name: String)                     extends AnyVal
final case class Secret(secret: String)                    extends AnyVal
final case class ContainerName(name: String)               extends AnyVal
final case class OperationDescription(description: String) extends AnyVal

sealed trait Expression
sealed trait Action

final case class And(left: Expression, right: Expression) extends Expression
final case class Copy(from: Seq[Path], to: Path)          extends Expression with Action
final case class Move(from: Seq[Path], to: Path)          extends Expression with Action
final case class Remove(from: Seq[Path])                  extends Expression with Action

final case class FileOperationFailed(file: Path, th: Throwable)
final case class OperationResult(succeed: Long, errors: Vector[FileOperationFailed])
final case class CredsForPath(path: Path, account: Account, container: ContainerName)

object types {
  type CREDS                    = Map[Path, Secret]
  type DECOMPOSED_PATH          = (Account, ContainerName, Path)
  type REQUIRED_CREDS_FOR_PATHS = Map[(Account, ContainerName), Vector[Path]]
  type OPERATIONS_SUMMARY       = Map[OperationDescription, OperationResult]
}
