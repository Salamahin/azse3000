package com.aswatson.aswrdm.azse3000

import cats.Id
import com.aswatson.aswrdm.azse3000.shared._

package object program {
  implicit val parId = new Parallel[Id] {
    override def traverse[T, U](items: Seq[T])(action: T => Id[U]): Id[Seq[U]] = items.map(action)

    override def zip[T, U](first: Id[T], second: Id[U]): (T, U) = (first, second)
  }

  def path(str: String): ParsedPath = {
    val pattern = "([\\w-]+)@([\\w-]+):/([\\w-./=]+)?".r

    str match {
      case pattern(container, account, relative) =>
        ParsedPath(Account(account), Container(container), RelativePath(relative))
    }
  }

  implicit class ParsedPathOps(p: ParsedPath) {
    def toPath = Path(s"${p.container.name}@${p.account.name}:/${p.relative.path}")
    def show   = s"${p.container.name}@${p.account.name}:/${p.relative.path}"
  }
}
