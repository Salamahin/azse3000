package com.aswatson.aswrdm.azse3000.program

import cats.Id
import com.aswatson.aswrdm.azse3000.shared.Parallel

object id {
  implicit val parId = new Parallel[Id] {
    override def traverse[T, U](items: Seq[T])(action: T => Id[U]): Id[Seq[U]] = items.map(action)

    override def zip[T, U](first: Id[T], second: Id[U]): (T, U) = (first, second)
  }
}
