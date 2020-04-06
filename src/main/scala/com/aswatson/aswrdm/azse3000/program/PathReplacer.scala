package com.aswatson.aswrdm.azse3000.program

import com.aswatson.aswrdm.azse3000.shared._

import scala.annotation.tailrec
import scala.collection.mutable

class PathReplacer {
  def replace(expr: Expression[Path], paths: Map[Path, ParsedPath]) = {

    @tailrec
    def iter(
      expr: List[Expression[Path]],
      acc: mutable.Map[Expression[Path], () => Expression[ParsedPath]]
    ): mutable.Map[Expression[Path], () => Expression[ParsedPath]] = {
      expr match {
        case Nil => acc

        case (and @ And(left, right)) :: tail =>
          iter(
            left :: right :: tail,
            acc += (and -> (() => And(acc(left)(), acc(right)())))
          )

        case (cp @ Copy(from, to)) :: tail => iter(tail, acc += (cp -> (() => Copy(from.map(paths), paths(to)))))
        case (mv @ Move(from, to)) :: tail => iter(tail, acc += (mv -> (() => Move(from.map(paths), paths(to)))))
        case (rm @ Remove(from)) :: tail   => iter(tail, acc += (rm -> (() => Remove(from.map(paths)))))
      }
    }

    iter(expr :: Nil, mutable.Map.empty)(expr)()
  }
}
