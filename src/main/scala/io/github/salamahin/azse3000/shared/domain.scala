package io.github.salamahin.azse3000.shared

sealed trait AzException
sealed trait Fatal
final case class AggregatedFatals(problems: Vector[AzException with Fatal]) extends Exception with AzException with Fatal
final case class MalformedCommand(msg: String)                            extends Exception(msg) with AzException with Fatal
final case class ContainerListingFailed(msg: String, cause: Exception)    extends Exception(msg, cause) with AzException with Fatal

final case class BlobCreationFailed(msg: String, cause: Exception)        extends Exception(msg, cause) with AzException
final case class BlobDeletionFailed(msg: String, cause: Exception)        extends Exception(msg, cause) with AzException
final case class BlobCopyStatusCheckFailed(msg: String, cause: Exception) extends Exception(msg, cause) with AzException

final case class Path(path: String)   extends AnyVal
final case class Prefix(path: String) extends AnyVal
final case class ParsedPath(account: Account, container: Container, prefix: Prefix)

final case class Command(cmd: String)                      extends AnyVal
final case class Account(name: String)                     extends AnyVal
final case class Secret(secret: String)                    extends AnyVal
final case class Container(name: String)                   extends AnyVal
final case class Description(description: String) extends AnyVal

sealed trait Expression[P]
sealed trait Action[P]
final case class And[P](left: Expression[P], right: Expression[P]) extends Expression[P]
final case class Copy[P](from: Seq[P], to: P)                      extends Expression[P] with Action[P]
final case class Move[P](from: Seq[P], to: P)                      extends Expression[P] with Action[P]
final case class Remove[P](from: Seq[P])                           extends Expression[P] with Action[P]
final case class Count[P](in: Seq[P])                              extends Expression[P] with Action[P]

final case class ActionFailed(msg: String, th: Throwable)
final case class Summary(succeed: Long, errors: Vector[ActionFailed])

object types {
  type CREDS = Map[(Account, Container), Secret]
}
