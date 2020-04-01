package com.aswatson.aswrdm.azse3000.program

import com.aswatson.aswrdm.azse3000.shared.{And, Copy, Expression, Move, Path, Remove}
import org.scalatest.{FunSuite, Matchers}

class PathReplacerTest extends FunSuite with Matchers {
  test("can replace path") {
    val expr = And(
      And(
        Copy(Path("cp_from") :: Nil, Path("cp_to")),
        Move(Path("mv_from") :: Nil, Path("mv_to"))
      ),
      Remove(Path("rm_from") :: Nil)
    )

    val list = new PathReplacer {}.replace(expr)
    list should be ('empty)
  }
}
