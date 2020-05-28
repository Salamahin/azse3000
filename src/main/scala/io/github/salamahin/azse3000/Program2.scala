package io.github.salamahin.azse3000
import java.util.concurrent.TimeUnit

import scalaz.{Applicative, Free, FreeAp, Inject, ~>}
import zio.{UIO, ZIO}

object Program2 extends zio.App {

  sealed trait Algebra[A]
  final case class Foo() extends Algebra[Unit]
  final case class Bar() extends Algebra[Unit]
  final case class Baz() extends Algebra[Unit]
  final case class Qux() extends Algebra[Unit]

  final case class MyAlgebra[F[_]]()(implicit inj: Inject[Algebra, F]) {
    def foo() = inj(Foo())
    def bar() = inj(Bar())
    def baz() = inj(Baz())
    def qux() = inj(Qux())
  }

  object MyAlgebra {
    implicit def myAlgebra[F[_]](implicit I: Inject[Algebra, F]) = new MyAlgebra[F]
  }

  val interpret =
    new (Algebra ~> UIO) {
      override def apply[A](fa: Algebra[A]): UIO[A] =
        fa match {
          case Foo() =>
            UIO {
              println("foo start")
              TimeUnit.SECONDS.sleep(5)
              println("foo end")
            }
          case Bar() =>
            UIO {
              println("bar start")
              TimeUnit.SECONDS.sleep(5)
              println("bar end")
            }
          case Baz() =>
            UIO {
              println("baz start")
              TimeUnit.SECONDS.sleep(5)
              println("baz end")
            }
          case Qux() =>
            UIO {
              println("qux start")
              TimeUnit.SECONDS.sleep(5)
              println("foo end")
            }
        }
    }

  def program(implicit alg: MyAlgebra[Algebra]) = {
    import alg._
    import scalaz.syntax.applicative._

    for {
      _ <- Free.liftF((FreeAp.lift(foo()) |@| FreeAp.lift(bar())) {
        case (_, _) => ()
      })
      _ <- Free.liftF(FreeAp.lift(baz()))
      _ <- Free.liftF(FreeAp.lift(qux()))
    } yield ()
  }

  final case class ParallelInterpreter[G[_]](f: Algebra ~> G)(implicit ev: Applicative[G]) extends (FreeAp[Algebra, *] ~> G) {

    override def apply[A](fa: FreeAp[Algebra, A]): G[A] = fa.foldMap(f)
  }

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = {
    import MyAlgebra._
    import zio._
    import zio.interop.scalaz72._

    implicit val parallelTaskApplicative = new Applicative[UIO] {
      def point[A](a: => A)                                 = UIO(a)
      def ap[A, B](a: => UIO[A])(f: => UIO[A => B]): UIO[B] = apply2(f, a)(_(_))
      override def apply2[A, B, C](a: => UIO[A], b: => UIO[B])(f: (A, B) => C): UIO[C] = {
        (a zipWithPar b)(f)
      }
    }

    program
      .foldMap(ParallelInterpreter(interpret)(parallelTaskApplicative))
      .map(_ => 0)
  }
}
