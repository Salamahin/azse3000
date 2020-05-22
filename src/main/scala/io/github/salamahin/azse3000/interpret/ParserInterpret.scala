package io.github.salamahin.azse3000.interpret
import cats.~>
import io.github.salamahin.azse3000.shared.{Parse, ParseCommand}
import zio.UIO

class ParserInterpret extends (Parse ~> UIO) {
  override def apply[A](fa: Parse[A]): UIO[A] =
    fa match {
      case ParseCommand(cmd) => ???
    }
}
