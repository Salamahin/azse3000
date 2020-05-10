package io.github.salamahin.azse3000.shared

sealed trait AzseException
final case class MalformedCommand(msg: String)               extends Exception(msg) with AzseException
final case class AzureFailure(msg: String, cause: Exception) extends Exception(msg, cause) with AzseException

final case class Command(cmd: String)                     extends AnyVal
final case class Account(name: String)                    extends AnyVal
final case class Secret(secret: String)                   extends AnyVal
final case class Container(name: String)                  extends AnyVal
final case class Description(description: String)         extends AnyVal
final case class Prefix(path: String)                     extends AnyVal
final case class Path(account: Account, container: Container, prefix: Prefix)

sealed trait Expression
sealed trait Action
final case class And(left: Expression, right: Expression) extends Expression
final case class Copy(from: Seq[Path], to: Path)          extends Expression with Action
final case class Move(from: Seq[Path], to: Path)          extends Expression with Action
final case class Remove(from: Seq[Path])                  extends Expression with Action
final case class Count(in: Seq[Path])                     extends Expression with Action
final case class Size(in: Seq[Path])                      extends Expression with Action

sealed trait Summary
final case class CopySummary(succeed: Long)   extends Summary
final case class MoveSummary(succeed: Long)   extends Summary
final case class RemoveSummary(succeed: Long) extends Summary
final case class CountSummary(count: Long)    extends Summary
final case class SizeSummary(bytes: Long)     extends Summary

final case class InterpretationReport(summary: Summary, errors: Vector[AzureFailure])
