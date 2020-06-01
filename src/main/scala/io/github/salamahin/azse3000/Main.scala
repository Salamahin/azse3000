package io.github.salamahin.azse3000
import java.net.URI

import cats.arrow.FunctionK
import cats.data.Kleisli
import cats.free.Free
import com.microsoft.azure.storage.StorageCredentialsSharedAccessSignature
import com.microsoft.azure.storage.blob.{CloudBlobContainer, CloudBlockBlob}
import io.github.salamahin.azse3000.delay.DelayInterpreter
import io.github.salamahin.azse3000.parsing.ParseCommandInterpreter
import io.github.salamahin.azse3000.shared._
import io.github.salamahin.azse3000.ui.UIInterpreter
import pureconfig.{ConfigReader, ConfigSource}
import zio.clock.Clock
import zio.{URIO, ZIO}

object Main extends zio.App {
  private def mapReader[K, V](f: String => K)(implicit r: ConfigReader[Map[String, V]]) =
    r
      .map {
        _.map {
          case (x, y) => f(x) -> y
        }
      }

  private def toBlob(path: Path) =
    new CloudBlockBlob(
      URI.create(s"https://${path.account.storage}.blob.core.windows.net/${path.container.name}/${path.prefix.value}"),
      new StorageCredentialsSharedAccessSignature(path.sas.secret)
    )

  private def toContainer(path: Path) =
    new CloudBlobContainer(
      URI.create(s"https://${path.account.storage.account}.blob.core.windows.net/${path.container.name}"),
      new StorageCredentialsSharedAccessSignature(path.sas.secret)
    )

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = {
    import pureconfig.generic.auto._
    implicit val environmentReader       = mapReader[Container, Secret](Container)
    implicit val environmentConfigReader = mapReader[EnvironmentAlias, EnvironmentConfig](EnvironmentAlias)

//    val conf = ConfigSource
//      .file("secrets.conf")
//      .loadOrThrow[Config]
//
//    val interpreter =
//      new UIInterpreter or
//        (new DelayInterpreter or
//          (new BlobStorageInterpreter(toContainer, toBlob, 2) or
//            new ParseCommandInterpreter(conf)))
//
//    import zio.interop.catz._
//
//    println(
//      """
//        |
//        | ▄▄▄      ▒███████▒  ██████ ▓█████
//        |▒████▄    ▒ ▒ ▒ ▄▀░▒██    ▒ ▓█   ▀
//        |▒██  ▀█▄  ░ ▒ ▄▀▒░ ░ ▓██▄   ▒███
//        |░██▄▄▄▄██   ▄▀▒   ░  ▒   ██▒▒▓█  ▄
//        | ▓█   ▓██▒▒███████▒▒██████▒▒░▒████▒
//        | ▒▒   ▓▒█░░▒▒ ▓░▒░▒▒ ▒▓▒ ▒ ░░░ ▒░ ░
//        |  ▒   ▒▒ ░░░▒ ▒ ░ ▒░ ░▒  ░ ░ ░ ░  ░
//        |  ░   ▒   ░ ░ ░ ░ ░░  ░  ░     ░
//        |      ░  ░  ░ ░          ░     ░  ░
//        |          ░
//        |
//        |""".stripMargin
//    )
//
//    type ParIntepreter[A] = Kleisli[URIO[Clock, *], Free[Program.App, scala.Either[AzseException, Unit]], A]
//    val a = new FunctionK[Program.App, ParIntepreter] {
//      override def apply[A](fa: Program.App[A]): ParIntepreter[A] = ???
//    }

//    Program.apply
//      .foldMap(a)
//      .map(_ => 0)

    ???
  }
}
