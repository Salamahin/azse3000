package io.github.salamahin.azse3000.shared

sealed trait AzseException
final case class MalformedCommand(msg: String)               extends Exception(msg) with AzseException
final case class AzureFailure(msg: String, cause: Throwable) extends Exception(msg, cause) with AzseException

final case class Command(cmd: String)      extends AnyVal
final case class Account(name: String)     extends AnyVal
final case class Container(name: String)   extends AnyVal
final case class Prefix(value: String)     extends AnyVal
final case class Environment(name: String) extends AnyVal
final case class Secret(secret: String) extends AnyVal {
  override def toString: String = "***"
}

final case class Path(account: Account, container: Container, prefix: Prefix, sas: Secret) {
  override def toString: String = s"${container.name}@${account.name}:/${prefix.value}"
}

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

final case class Description(description: String)
final case class InterpretationReport(description: Description, summary: Summary, errors: Vector[AzureFailure])

final case class EnvironmentConfig(account: Account, creds: Map[Container, Secret])
final case class Config(value: Map[Environment, EnvironmentConfig]) extends AnyVal
