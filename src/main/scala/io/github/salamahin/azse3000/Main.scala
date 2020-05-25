package io.github.salamahin.azse3000
import java.net.URI

import com.microsoft.azure.storage.StorageCredentialsSharedAccessSignature
import com.microsoft.azure.storage.blob.{CloudBlobContainer, CloudBlockBlob}
import io.github.salamahin.azse3000.blobstorage.BlobStorageInterpreter
import io.github.salamahin.azse3000.delay.DelayInterpreter
import io.github.salamahin.azse3000.parsing.ParseCommandInterpreter
import io.github.salamahin.azse3000.shared._
import io.github.salamahin.azse3000.ui.UIInterpreter
import pureconfig.{ConfigReader, ConfigSource}
import zio.ZIO

object Main extends zio.App {
  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = {
    def mapReader[K, V](f: String => K)(implicit r: ConfigReader[Map[String, V]]) =
      r
        .map {
          _.map {
            case (x, y) => f(x) -> y
          }
        }

    import pureconfig.generic.auto._
    implicit val environmentReader       = mapReader[Container, Secret](Container)
    implicit val environmentConfigReader = mapReader[Environment, EnvironmentConfig](Environment)

    val conf = ConfigSource
      .file("secrets.conf")
      .loadOrThrow[Config]

    def toBlob(path: Path) =
      new CloudBlockBlob(
        URI.create(s"https://${path.account.name}.blob.core.windows.net/${path.container.name}/${path.prefix.value}"),
        new StorageCredentialsSharedAccessSignature(path.sas.secret)
      )

    def toContainer(path: Path) =
      new CloudBlobContainer(
        URI.create(s"https://${path.account.name}.blob.core.windows.net/${path.container.name}"),
        new StorageCredentialsSharedAccessSignature(path.sas.secret)
      )

    val interpreter = new UIInterpreter or
      (new DelayInterpreter or
        (new BlobStorageInterpreter(toContainer, toBlob, 5000) or
          new ParseCommandInterpreter))

    import zio.interop.catz._
    Program.apply
      .foldMap(interpreter)
      .map(_ => 0)
  }
}
