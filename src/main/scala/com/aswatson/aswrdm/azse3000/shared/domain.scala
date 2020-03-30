package com.aswatson.aswrdm.azse3000.shared

sealed trait Fatal
sealed trait Aggregate
final case class InvalidCommand(msg: String)                         extends Exception with Fatal with Aggregate
final case class MalformedPath(msg: String)                          extends Exception with Fatal with Aggregate
final case class FileSystemFailure(msg: String, cause: Throwable)    extends Exception with Fatal with Aggregate
final case class AggregatedFatal(reasons: Seq[Fatal with Aggregate]) extends Exception with Fatal

final case class Path(path: String)         extends AnyVal
final case class RelativePath(path: String) extends AnyVal
final case class ParsedPath(account: Account, container: Container, relative: RelativePath) {
  def resolve(other: RelativePath) = { //fixme typeclass?
    val thisPaths = relative.path.split("/").toList
    val thatPaths = other.path.split("/").toList

    def iter(thisPaths: List[String], thatPaths: List[String], acc: List[String]): List[String] = {
      val thisHead :: thisTail = thisPaths
      val thatHead :: thatTail = thatPaths

      if(thisHead == thatHead) iter(thisTail, thatTail, acc :+ thisHead)
      else acc ++ thatPaths
    }

    ParsedPath(account, container, RelativePath(iter(thisPaths, thatPaths, Nil).mkString("/")))
  }
}

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

final case class OperationFailure(msg: String, th: Throwable)
final case class OperationResult(succeed: Long, errors: Vector[OperationFailure])

object types {
  type CREDS = Map[(Account, Container), Secret]
}
