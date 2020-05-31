package io.github.salamahin.azse3000.blobstorage
import cats.{InjectK, Monad}
import com.microsoft.azure.storage.blob.CloudBlockBlob
import io.github.salamahin.azse3000.shared.{AzureFailure, Path}

sealed trait MetafilelessOps[T]
final case class StartListing2(inPath: Path)             extends MetafilelessOps[Either[AzureFailure, ListingPage]]
final case class ContinueListing2(prevPage: ListingPage) extends MetafilelessOps[ListingPage]

final class Metafileless[F[_]]()(implicit inj: InjectK[MetafilelessOps, F]) {
  def initPage(inPath: Path)     = inj(StartListing2(inPath))
  def nextPage(tkn: ListingPage) = inj(ContinueListing2(tkn))
}

object Metafileless {
  implicit def metafiless[F[_]](implicit inj: InjectK[MetafilelessOps, F]): Metafileless[F] = new Metafileless[F]
}

object MetafilessBlobStorageProgram {

  type Algebra[T] = MetafilelessOps[T]
  final case class MappedBlob[T](src: CloudBlockBlob, mapped: T)

  def program(implicit m: Metafileless[Algebra]) = {
    import cats.implicits._
    import io.github.salamahin.azse3000.shared.Program._
    import m._

    def listAndProcessBlobs[F[_], T](from: Path)(f: CloudBlockBlob => Algebra[T]) = {
      def mapBlobs(thatPage: ListingPage, acc: Vector[MappedBlob[T]]) =
        thatPage
          .blobs
          .toVector
          .traverse(blob => f(blob).liftFA.map(MappedBlob(blob, _)))
          .map(x => acc ++ x)

      def mapThatPageAndListNext(thatPage: ListingPage, acc: Vector[MappedBlob[T]]) =
        (nextPage(thatPage).liftFA map2 mapBlobs(thatPage, acc)) {
          case (nextPage, mapped) => (nextPage, mapped)
        }

      for {
        initPage <- initPage(from).liftFA.liftFree.toEitherT
        initAcc = Vector.empty[MappedBlob[T]]

        mapped <- Monad[PRG[Algebra, *]]
          .tailRecM(initPage, initAcc) {
            case (page, acc) =>
              if (page.hasNext) mapThatPageAndListNext(page, acc).liftFree.map(_.asLeft[Vector[MappedBlob[T]]])
              else mapBlobs(page, acc).liftFree.map(_.asRight[(ListingPage, Vector[MappedBlob[T]])])
          }
          .toRightEitherT[AzureFailure]

      } yield mapped
    }
  }
}
