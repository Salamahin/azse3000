package com.aswatson.aswrdm.azse3000.program

import com.aswatson.aswrdm.azse3000.shared.{And, Expression, Path}

trait PathReplacer {
  def replace(expr: Expression[Path]) = {
    def iter(exprs: List[Expression[Path]], acc: List[Expression[Path]]): List[Expression[Path]] = {

      exprs match {
        case Nil                      => acc
        case And(left, right) :: tail => iter(left :: right :: tail, acc)
        case e :: tail                => iter(tail, acc :+ e)
      }

    }

    iter(expr :: Nil, Nil)
  }
}
