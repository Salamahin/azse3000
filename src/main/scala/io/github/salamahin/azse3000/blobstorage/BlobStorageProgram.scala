package io.github.salamahin.azse3000.blobstorage
import cats.data.EitherK

class BlobStorageProgram[F[_]](implicit m: BlobStorage[EitherK[BlobStorageOps, F, *]]) {

}
