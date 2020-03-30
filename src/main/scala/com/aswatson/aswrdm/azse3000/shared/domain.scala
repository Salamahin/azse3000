package com.aswatson.aswrdm.azse3000.shared

sealed trait Fatal
sealed trait Aggregate
final case class InvalidCommand(msg: String)                         extends Exception with Fatal with Aggregate
final case class MalformedPath(msg: String)                          extends Exception with Fatal with Aggregate
final case class FileSystemFailure(msg: String, cause: Throwable)    extends Exception with Fatal with Aggregate
final case class AggregatedFatal(reasons: Seq[Fatal with Aggregate]) extends Exception with Fatal

final case class Path(path: String)         extends AnyVal
final case class RelativePath(path: String) extends AnyVal
final case class FullPath(account: Account, container: Container, relative: RelativePath) {
  def resolve(other: RelativePath) = copy(relative = RelativePath(s"${relative.path}/${other.path}"))
}

final case class Command(cmd: String)                      extends AnyVal
final case class Account(name: String)                     extends AnyVal
final case class Secret(secret: String)                    extends AnyVal
final case class Container(name: String)                   extends AnyVal
final case class OperationDescription(description: String) extends AnyVal

sealed trait Expression
sealed trait Action
final case class And(left: Expression, right: Expression) extends Expression
final case class Copy(from: Seq[Path], to: Path)          extends Expression with Action
final case class Move(from: Seq[Path], to: Path)          extends Expression with Action
final case class Remove(from: Seq[Path])                  extends Expression with Action

final case class OperationFailure(msg: String, th: Throwable)
final case class OperationResult(succeed: Long, errors: Vector[OperationFailure])

object types {
  type CREDS = Map[(Account, Container), Secret]
}
