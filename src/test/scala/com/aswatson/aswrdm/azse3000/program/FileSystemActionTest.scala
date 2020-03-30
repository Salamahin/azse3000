package com.aswatson.aswrdm.azse3000.program

import cats.Id
import com.aswatson.aswrdm.azse3000.program.FileSystemActionTest.{IdEndpoint, InMemoryIdFileSystem}
import com.aswatson.aswrdm.azse3000.shared._
import org.scalatest.{FunSuite, Matchers}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

object FileSystemActionTest {

  class InMemoryIdFileSystem extends FileSystem[Id, ParsedPath, (Account, Container)] {
    import cats.syntax.either._

    private val _files: mutable.Map[(Account, Container), ListBuffer[ParsedPath]] =
      mutable.Map.empty.withDefaultValue(ListBuffer.empty)

    private val _badFiles: mutable.Set[ParsedPath]                = mutable.Set.empty
    private val _badContainers: mutable.Set[(Account, Container)] = mutable.Set.empty

    def withFileIn(fp: ParsedPath) = {
      addToFs(fp)
      this
    }

    def files = _files.values.flatten

    private def isBadFile(blob: ParsedPath)                = _badFiles.contains(blob)
    private def isBadContainer(cont: (Account, Container)) = _badContainers.contains(cont)

    private def addToFs(blob: ParsedPath): Unit      = _files((blob.account, blob.container)) :+= blob
    private def removeFromFs(blob: ParsedPath): Unit = _files((blob.account, blob.container)) -= blob

    override def copyContent(fromBlob: ParsedPath, toBlob: ParsedPath): Id[Either[OperationFailure, Unit]] =
      if (isBadFile(fromBlob)) OperationFailure(null, null).asLeft
      else addToFs(toBlob).asRight

    override def remove(blob: ParsedPath): Id[Either[OperationFailure, Unit]] =
      if (isBadFile(blob)) OperationFailure(null, null).asLeft
      else removeFromFs(blob).asRight

    override def foreachBlob[U](container: (Account, Container), prefix: RelativePath)(
      action: Seq[ParsedPath] => Id[Seq[U]]
    ): Id[Either[FileSystemFailure, Seq[U]]] =
      if (isBadContainer(container)) FileSystemFailure(null, null).asLeft
      else action(_files(container)).asRight
  }

  class IdEndpoint extends Endpoint[Id, ParsedPath, (Account, Container)] {
    override def toBlob(p: ParsedPath): Id[ParsedPath] = p

    override def toContainer(p: ParsedPath): Id[(Account, Container)] =
      (p.account, p.container)

    override def showBlob(p: ParsedPath): Id[String] =
      s"${p.account.name}@${p.container.name}:/${p.relative.path}"

    override def showContainer(p: (Account, Container)): Id[String] = p match {
      case (acc, cont) => s"${acc.name}@${cont.name}"
    }

    override def showPath(p: ParsedPath): Id[String] =
      s"${p.account.name}@${p.container.name}:/${p.relative}"
  }
}

class FileSystemActionTest extends FunSuite with Matchers {
  def actionsOn(fs: InMemoryIdFileSystem) = new FileSystemAction[Id, ParsedPath, (Account, Container)](
    new IdEndpoint,
    id.parId,
    fs
  )

  test("can cp blob") {
    val source  = ParsedPath(Account("a1"), Container("c1"), RelativePath("a/b/c"))
    val destination  = ParsedPath(Account("a1"), Container("c1"), RelativePath("a/b"))
    val expected = ParsedPath(Account("a2"), Container("c2"), RelativePath("a/b/c"))

    val fs = (new InMemoryIdFileSystem)
      .withFileIn(source)

    val evaluated = actionsOn(fs).evaluate(Copy(source :: Nil, destination))

    evaluated should be('right)
    fs.files should contain only (source, expected)
  }
}
