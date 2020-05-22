package io.github.salamahin.azse3000
import cats.InjectK
import cats.arrow.FunctionK
import cats.effect.IO
import io.github.salamahin.azse3000.interpret._
import io.github.salamahin.azse3000.shared._
import zio.ZIO

object Main extends App {
  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = {

    implicit val int = new UICompiler
//      new CommandParserCompiler or
//      new DelayCompiler or
//      new VaultCompiler(Map.empty) or
//      new BlobStorageAPICompiler(???, ???, ???)

    implicit val ui = UserInterface[IO]()
//    implicit val parser = Parser[URIO[Clock, *]]()
//    implicit val vault  = VaultStorage[URIO[Clock, *]]()
//    implicit val azure  = BlobStorage[URIO[Clock, *]]()
//    implicit val delays = Delays[URIO[Clock, *]]()

//    Program
//      .program[URIO[Clock, *]]
//      .foldMap(int)

    ???
  }
}
