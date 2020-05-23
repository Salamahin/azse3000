package io.github.salamahin.azse3000
import java.net.URI

import com.microsoft.azure.storage.StorageCredentialsSharedAccessSignature
import com.microsoft.azure.storage.blob.{CloudBlobContainer, CloudBlockBlob}
import io.github.salamahin.azse3000.blobstorage.BlobStorageInterpreter
import io.github.salamahin.azse3000.delay.DelayInterpreter
import io.github.salamahin.azse3000.parsing.ParseCommandInterpreter
import io.github.salamahin.azse3000.shared.{Account, Container, Path, Secret}
import io.github.salamahin.azse3000.ui.UIInterpreter
import io.github.salamahin.azse3000.vault.VaultInterpreter
import pureconfig.ConfigSource
import zio.ZIO

object Main extends zio.App {
  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = {
    import pureconfig.generic.auto._
    import zio.interop.catz.core._

    val conf = ConfigSource.file("secrets.conf").load[Config] match {
      case Right(c) => c
      case Left(_)  => Config(Map.empty)
    }

    val creds = conf
      .secrets
      .toSeq
      .flatMap {
        case (acc, tokens) =>
          tokens
            .map {
              case (cont, sas) => (Account(acc), Container(cont)) -> Secret(sas)
            }
      }
      .toMap

    def toBlob(path: Path, secret: Secret) =
      new CloudBlockBlob(
        URI.create(s"https://${path.account.name}.blob.core.windows.net/${path.container.name}/${path.prefix.path}"),
        new StorageCredentialsSharedAccessSignature(secret.secret)
      )

    def toContainer(path: Path, secret: Secret) =
      new CloudBlobContainer(
        URI.create(s"https://${path.account.name}.blob.core.windows.net/${path.container.name}"),
        new StorageCredentialsSharedAccessSignature(secret.secret)
      )

    val interpreter = new UIInterpreter or
      (new DelayInterpreter or
        (new BlobStorageInterpreter(toContainer, toBlob, 5000) or
          (new ParseCommandInterpreter or
            new VaultInterpreter(creds))))

    Program.apply
      .foldMap(interpreter)
      .map(_ => 0)
  }
}
