package com.aswatson.aswrdm.azse3000.program

import cats.{Id, Monad}
import com.aswatson.aswrdm.azse3000.program.ContinuableTest.{Continuation, ParId}
import com.aswatson.aswrdm.azse3000.shared.Parallel
import org.scalatest.{FunSuite, Matchers}

object ContinuableTest {
  case class Continuation(value: Int, next: Option[Continuation])

  object ParId extends Parallel[Id] {
    override def traverse[T, U](items: Seq[T])(action: T => Id[U]): Id[Seq[U]] = items.map(action)

    override def traverseN[T, U](items: Seq[T])(action: T => Id[U]): Id[Seq[U]] = items.map(action)

    override def zip[T, U](first: Id[T], second: Id[U]): (T, U) = (first, second)
  }
}

class ContinuableTest extends FunSuite with Matchers {
  private def continuation(limit: Int): Continuation = {
    def iter(more: Int, idx: Int): Option[Continuation] = {
      if (more == 0) None
      else Some(Continuation(idx, iter(more - 1, idx + 1)))
    }

    iter(limit, 0).get
  }

  private val continuable = new Continuable[Id] {}

  test("can continue") {
    val res = continuable.doAnd[Continuation, Int](
      () => continuation(3),
      c => c.next,
      c => c.value
    )(Monad[Id], ParId)

    (res: Seq[Int]) should contain inOrderOnly (0, 1, 2)
  }
}
