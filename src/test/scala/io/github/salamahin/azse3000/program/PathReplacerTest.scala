package io.github.salamahin.azse3000.program

import io.github.salamahin.azse3000.shared._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class PathReplacerTest extends AnyFunSuite with Matchers {
  private val rawPath1 = Path("cont@acc:/p1")
  private val rawPath2 = Path("cont@acc:/p2")
  private val rawPath3 = Path("cont@acc:/p3")
  private val rawPath4 = Path("cont@acc:/p3")
  private val rawPath5 = Path("cont@acc:/p3")

  private val parsedPath1 = path(rawPath1.path)
  private val parsedPath2 = path(rawPath2.path)
  private val parsedPath3 = path(rawPath3.path)
  private val parsedPath4 = path(rawPath3.path)
  private val parsedPath5 = path(rawPath3.path)

  private val pathsMapping = Map(
    rawPath1 -> parsedPath1,
    rawPath2 -> parsedPath2,
    rawPath3 -> parsedPath3,
    rawPath4 -> parsedPath4,
    rawPath5 -> parsedPath5
  )

  test("can replace path") {
    val expr = And(
      And(
        Copy(rawPath1 :: Nil, rawPath2),
        Move(rawPath3 :: Nil, rawPath4)
      ),
      Remove(rawPath5 :: Nil)
    )

    new PathReplacer().replace(expr, pathsMapping) should be(
      And(
        And(
          Copy(parsedPath1 :: Nil, parsedPath2),
          Move(parsedPath3 :: Nil, parsedPath4)
        ),
        Remove(parsedPath5 :: Nil)
      )
    )
  }
}
