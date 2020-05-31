package io.github.salamahin.azse3000.shared

trait ActionInterpret[T] {
  def run(term: Action): T
}
