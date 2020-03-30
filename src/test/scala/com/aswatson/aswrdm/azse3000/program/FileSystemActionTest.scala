package com.aswatson.aswrdm.azse3000.program

import cats.Id
import com.aswatson.aswrdm.azse3000.shared.{FileSystem, FileSystemFailure, OperationFailure, RelativePath}
import org.scalatest.{FunSuite, Matchers}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

object FileSystemActionTest {
  case class IdFile(cont: IdContainer, path: String)
  case class IdContainer(name: String)

  val fs = new FileSystem[Id, IdFile, IdContainer] {
    import cats.syntax.either._

    private val _files: mutable.Map[IdContainer, ListBuffer[IdFile]] =
      mutable.Map.empty.withDefaultValue(ListBuffer.empty)

    private val _badFiles: mutable.Set[IdFile]           = mutable.Set.empty
    private val _badContainers: mutable.Set[IdContainer] = mutable.Set.empty

    private def isBadFile(blob: IdFile)           = _badFiles.contains(blob)
    private def isBadContainer(cont: IdContainer) = _badContainers.contains(cont)

    private def addToFs(blob: IdFile): Unit      = _files(blob.cont) :+= blob
    private def removeFromFs(blob: IdFile): Unit = _files(blob.cont) -= blob

    override def copyContent(fromBlob: IdFile, toBlob: IdFile): Id[Either[OperationFailure, Unit]] =
      if (isBadFile(fromBlob)) OperationFailure(s"Failed to move ${fromBlob.path} to ${toBlob.path}", null).asLeft
      else addToFs(toBlob).asRight

    override def remove(blob: IdFile): Id[Either[OperationFailure, Unit]] =
      if (isBadFile(blob)) OperationFailure(s"Failed to remove ${blob.path}", null).asLeft
      else removeFromFs(blob).asRight

    override def foreachBlob[U](container: IdContainer, prefix: RelativePath)(
      action: Seq[IdFile] => Id[Seq[U]]
    ): Id[Either[FileSystemFailure, Seq[U]]] =
      if (isBadContainer(container)) FileSystemFailure(s"Failed to list ${container.name}", null).asLeft
      else action(_files(container)).asRight
  }
}

class FileSystemActionTest extends FunSuite with Matchers {
//  private val fs = new FileSystemAction[Id]()
}
