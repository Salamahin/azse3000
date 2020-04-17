package com.aswatson.aswrdm.azse3000.program

import cats.Eval
import com.aswatson.aswrdm.azse3000.shared._

class PathReplacer {
  def replace(expr: Expression[Path], paths: Map[Path, ParsedPath]) = {

    def trampolinedReplace(expr: Expression[Path]): Eval[Expression[ParsedPath]] = {
      expr match {
        case And(left, right) =>
          for {
            l <- Eval.defer(trampolinedReplace(left))
            r <- Eval.defer(trampolinedReplace(right))
          } yield And(l, r)

        case Copy(from, to) => Eval.now(Copy(from.map(paths), paths(to)))
        case Move(from, to) => Eval.now(Move(from.map(paths), paths(to)))
        case Remove(from)   => Eval.now(Remove(from.map(paths)))
        case Count(in)      => Eval.now(Count(in.map(paths)))
      }
    }

    trampolinedReplace(expr).value
  }
}
