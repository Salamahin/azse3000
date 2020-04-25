package io.github.salamahin.azse3000.azure

import cats.Id
import io.github.salamahin.azse3000.azure.ContinuableTest.Continuation
import io.github.salamahin.azse3000.program
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.annotation.tailrec

object ContinuableTest {
  case class Continuation(value: Int, next: Option[Continuation])
}

class ContinuableTest extends AnyFunSuite with Matchers {
  private def continuation(limit: Int): Continuation = {

    @tailrec
    def iter(more: Int, prev: Option[Continuation]): Option[Continuation] = {
      if (more == 0) prev
      else iter(more - 1, Some(Continuation(more - 1, prev)))
    }

    iter(limit, None).get
  }

  private val continuable = new Continuable[Id](program.parId)

  test("can continue") {
    val res = continuable.doAndContinue[Continuation, Int](
      () => continuation(3),
      c => c.next,
      c => c.value
    )

    (res: Seq[Int]) should contain inOrderOnly (0, 1, 2)
  }

  test("is stack safe") {
    continuable.doAndContinue[Continuation, Int](
      () => continuation(1000000),
      c => c.next,
      c => c.value
    )
  }
}
