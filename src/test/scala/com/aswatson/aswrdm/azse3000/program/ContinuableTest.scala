package com.aswatson.aswrdm.azse3000.program

import cats.Id
import com.aswatson.aswrdm.azse3000.program.ContinuableTest.Continuation
import org.scalatest.{FunSuite, Matchers}

import scala.annotation.tailrec

object ContinuableTest {
  case class Continuation(value: Int, next: Option[Continuation])
}

class ContinuableTest extends FunSuite with Matchers {
  private def continuation(limit: Int): Continuation = {

    @tailrec
    def iter(more: Int, prev: Option[Continuation]): Option[Continuation] = {
      if (more == 0) prev
      else iter(more - 1, Some(Continuation(more - 1, prev)))
    }

    iter(limit, None).get
  }

  private val continuable = new Continuable[Id](parId)

  test("can continue") {
    val res = continuable.doAnd[Continuation, Int](
      () => continuation(3),
      c => c.next,
      c => c.value
    )

    (res: Seq[Int]) should contain inOrderOnly (0, 1, 2)
  }

  test("is stack safe") {
    continuable.doAnd[Continuation, Int](
      () => continuation(1000000),
      c => c.next,
      c => c.value
    )
  }
}
