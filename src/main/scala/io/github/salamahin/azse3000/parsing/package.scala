package io.github.salamahin.azse3000
import cats.InjectK
import io.github.salamahin.azse3000.shared.{Command, Expression, MalformedCommand}

package object parsing {
  sealed trait ParsingOps[T]
  final case class ParseCommand(cmd: Command) extends ParsingOps[Either[MalformedCommand, Expression]]

  final class Parser[F[_]]()(implicit inj: InjectK[ParsingOps, F]) {
    def parseCommand(cmd: Command) = inj(ParseCommand(cmd))
  }

  object Parser {
    implicit def parser[F[_]](implicit I: InjectK[ParsingOps, F]): Parser[F] = new Parser[F]
  }
}
