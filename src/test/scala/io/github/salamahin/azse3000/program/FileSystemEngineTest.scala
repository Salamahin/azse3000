package io.github.salamahin.azse3000.program

import cats.Id
import io.github.salamahin.azse3000.program.FileSystemEngineTest.{IdEndpoint, InMemoryIdFileSystem}
import io.github.salamahin.azse3000.shared._
import org.scalatest.BeforeAndAfter
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

object FileSystemEngineTest {

  class InMemoryIdFileSystem extends FileSystem[Id, ParsedPath, (Account, Container)] {

    import cats.syntax.either._

    private val _files: mutable.Map[(Account, Container), ListBuffer[ParsedPath]] =
      mutable.Map.empty.withDefaultValue(ListBuffer.empty)

    private val _failedToCopyFiles: mutable.Set[ParsedPath]       = mutable.Set.empty
    private val _failedToRemoveFiles: mutable.Set[ParsedPath]     = mutable.Set.empty
    private val _badContainers: mutable.Set[(Account, Container)] = mutable.Set.empty

    def addBlob(fp: ParsedPath) = {
      addToFs(fp)
      this
    }

    def failOnCopy(fp: ParsedPath) = {
      _failedToCopyFiles += fp
      this
    }

    def failOnRemove(fp: ParsedPath) = {
      _failedToRemoveFiles += fp
      this
    }

    def failOnList(acc: String, cont: String) = {
      _badContainers += ((Account(acc), Container(cont)))
      this
    }

    def files = _files.values.flatten

    private def addToFs(blob: ParsedPath): Unit = _files((blob.account, blob.container)) :+= blob

    private def removeFromFs(blob: ParsedPath): Unit = _files((blob.account, blob.container)) -= blob

    override def copyContent(fromBlob: ParsedPath, toBlob: ParsedPath): Id[Either[Throwable, Unit]] =
      if (_failedToCopyFiles.contains(fromBlob)) new IllegalStateException("File is bad").asLeft
      else addToFs(toBlob).asRight

    override def remove(blob: ParsedPath): Id[Either[Throwable, Unit]] =
      if (_failedToRemoveFiles.contains(blob)) new IllegalStateException("File is bad").asLeft
      else removeFromFs(blob).asRight

    override def foreachBlob[U](location: (Account, Container), prefix: Prefix)(
      action: Seq[ParsedPath] => Id[Seq[U]]
    ): Id[Either[Throwable, Seq[U]]] =
      if (_badContainers.contains(location)) new IllegalStateException("Container is bad").asLeft
      else {
        val allFiles = _files(location)
        val subfiles = allFiles.filter(_.prefix.path.startsWith(prefix.path))
        action(subfiles.toSeq).asRight
      }
  }

  class IdEndpoint extends Endpoint[Id, ParsedPath, (Account, Container)] {
    override def toBlob(p: ParsedPath): Id[ParsedPath] = p

    override def toContainer(p: ParsedPath): Id[(Account, Container)] =
      (p.account, p.container)

    override def containerPath(p: (Account, Container)): Id[Path] = p match {
      case (acc, cont) => Path(s"${acc.name}@${cont.name}")
    }

    override def blobPath(p: ParsedPath): Id[Path] = p.toPath

    override def showPath(p: ParsedPath): Id[String] = p.show
  }
}

class FileSystemEngineTest extends AnyFunSuite with Matchers with BeforeAndAfter {

  object paths {
    object source {
      val a_b = path("cont@acc:/a/b/")
    }

    object dest {
      val e_f_g = path("cont@acc:/e/f/g/")
    }

    object initial {
      val a_b_c   = path("cont@acc:/a/b/c")
      val a_b_c_d = path("cont@acc:/a/b/c/d")
    }

    object expected {
      val e_f_g_c   = path("cont@acc:/e/f/g/c")
      val e_f_g_c_d = path("cont@acc:/e/f/g/c/d")
    }
  }

  var action: FileSystemEngine[Id, ParsedPath, (Account, Container)] = _
  var fs: InMemoryIdFileSystem                                       = _

  before {
    fs = (new InMemoryIdFileSystem)
      .addBlob(paths.initial.a_b_c_d)
      .addBlob(paths.initial.a_b_c)

    action = new FileSystemEngine[Id, ParsedPath, (Account, Container)](
      new IdEndpoint,
      parId,
      fs
    )
  }

  test("can copy blobs") {
    action.evaluate(Copy(paths.source.a_b :: Nil, paths.dest.e_f_g)) should be(Symbol("right"))
    fs.files should contain only (
      paths.initial.a_b_c_d,
      paths.initial.a_b_c,
      paths.expected.e_f_g_c,
      paths.expected.e_f_g_c_d
    )
  }

  test("can move blobs") {
    action.evaluate(Move(paths.source.a_b :: Nil, paths.dest.e_f_g)) should be(Symbol("right"))
    fs.files should contain only (
      paths.expected.e_f_g_c,
      paths.expected.e_f_g_c_d
    )
  }

  test("can remove blobs") {
    action.evaluate(Remove(paths.source.a_b :: Nil)) should be(Symbol("right"))
    fs.files should be(Symbol("empty"))
  }

  test("failure on container listing is fatal") {
    fs.failOnList("acc", "cont")

    action.evaluate(Remove(paths.source.a_b :: Nil)) should be(Symbol("left"))
  }

  test("copy of a bad blob is not fatal") {
    fs.failOnCopy(paths.initial.a_b_c)

    action.evaluate(Copy(paths.source.a_b :: Nil, paths.dest.e_f_g)) should be(Symbol("right"))
    fs.files should contain only (
      paths.initial.a_b_c,
      paths.initial.a_b_c_d,
      paths.expected.e_f_g_c_d
    )
  }

  test("move of a bad blob is not fatal") {
    fs.failOnCopy(paths.initial.a_b_c)
    fs.failOnRemove(paths.initial.a_b_c_d)

    action.evaluate(Move(paths.source.a_b :: Nil, paths.dest.e_f_g)) should be(Symbol("right"))
    fs.files should contain only (
      paths.initial.a_b_c,
      paths.initial.a_b_c_d,
      paths.expected.e_f_g_c_d
    )
  }

  test("remove of a bad blob is not fatal") {
    fs.failOnRemove(paths.initial.a_b_c)

    action.evaluate(Remove(paths.source.a_b :: Nil)) should be(Symbol("right"))
    fs.files should contain only (
      paths.initial.a_b_c
    )
  }
}
