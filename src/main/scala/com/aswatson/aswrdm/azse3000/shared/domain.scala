package com.aswatson.aswrdm.azse3000.shared

sealed trait Fatal
sealed trait Aggregate
final case class InvalidCommand(msg: String)                         extends Exception with Fatal with Aggregate
final case class MalformedPath(msg: String)                          extends Exception with Fatal with Aggregate
final case class FileSystemFailure(msg: String, cause: Throwable)    extends Exception with Fatal with Aggregate
final case class AggregatedFatal(reasons: Seq[Fatal with Aggregate]) extends Exception with Fatal

final case class Path(path: String)   extends AnyVal
final case class Prefix(path: String) extends AnyVal
final case class ParsedPath(account: Account, container: Container, prefix: Prefix)

final case class Command(cmd: String)                      extends AnyVal
final case class Account(name: String)                     extends AnyVal
final case class Secret(secret: String)                    extends AnyVal
final case class Container(name: String)                   extends AnyVal
final case class OperationDescription(description: String) extends AnyVal

sealed trait Expression[P]
sealed trait Action[P]
final case class And[P](left: Expression[P], right: Expression[P]) extends Expression[P]
final case class Copy[P](from: Seq[P], to: P)                      extends Expression[P] with Action[P]
final case class Move[P](from: Seq[P], to: P)                      extends Expression[P] with Action[P]
final case class Remove[P](from: Seq[P])                           extends Expression[P] with Action[P]
final case class Count[P](in: Seq[P])                              extends Expression[P] with Action[P]

final case class ActionFailed(msg: String, th: Throwable)
final case class EvaluationSummary(succeed: Long, errors: Vector[ActionFailed])

object types {
  type CREDS = Map[(Account, Container), Secret]
}
