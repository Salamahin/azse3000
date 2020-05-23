package io.github.salamahin.azse3000
import io.github.salamahin.azse3000.blobstorage.BlobStorageInterpreter
import io.github.salamahin.azse3000.delay.DelayInterpreter
import io.github.salamahin.azse3000.parsing.ParseCommandInterpreter
import io.github.salamahin.azse3000.ui.UIInterpreter
import io.github.salamahin.azse3000.vault.VaultInterpreter
import zio.ZIO

object Main extends zio.App {
  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = {
    import zio.interop.catz.core._

    val interpreter = new UIInterpreter or
      (new DelayInterpreter or
        (new BlobStorageInterpreter(???, ???, ???) or
          (new ParseCommandInterpreter or
            new VaultInterpreter(Map.empty))))

    Program.apply
      .foldMap(interpreter)
      .map(_ => 0)
  }
}
