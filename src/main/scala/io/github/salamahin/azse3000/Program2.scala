package io.github.salamahin.azse3000
import java.util.concurrent.TimeUnit

import cats.free.{Free, FreeApplicative}
import cats.{InjectK, ~>}
import zio.{UIO, ZIO}

//object Program2 extends zio.App {
//
//  sealed trait Algebra[A]
//  final case class Foo(i: Int) extends Algebra[Unit]
//  final case class Bar(i: Int) extends Algebra[Unit]
//  final case class Baz()       extends Algebra[Unit]
//  final case class Qux()       extends Algebra[Unit]
//
//  final case class MyAlgebra[F[_]]()(implicit inj: InjectK[Algebra, F]) {
//    def foo(i: Int) = inj(Foo(i))
//    def bar(i: Int) = inj(Bar(i))
//    def baz()       = inj(Baz())
//    def qux()       = inj(Qux())
//  }
//
//  object MyAlgebra {
//    implicit def myAlgebra[F[_]](implicit I: InjectK[Algebra, F]) = new MyAlgebra[F]
//  }
//
//  val interpret =
//    new (Algebra ~> UIO) {
//      override def apply[A](fa: Algebra[A]): UIO[A] =
//        fa match {
//          case Foo(i) =>
//            UIO {
//              println(s"foo($i) start")
//              TimeUnit.SECONDS.sleep(5)
//              println("foo end")
//            }
//          case Bar(i) =>
//            UIO {
//              println(s"bar($i) start")
//              TimeUnit.SECONDS.sleep(5)
//              println("bar end")
//            }
//          case Baz() =>
//            UIO {
//              println("baz start")
//              TimeUnit.SECONDS.sleep(5)
//              println("baz end")
//            }
//          case Qux() =>
//            UIO {
//              println("qux start")
//              TimeUnit.SECONDS.sleep(5)
//              println("qux end")
//            }
//        }
//    }
//
//  def program(implicit alg: MyAlgebra[Algebra]) = {
//    import alg._
//    import cats.implicits._
//
//    val is = Vector(1, 2, 3)
//
//    for {
//      _ <- Free.liftF(
//        is
//          .map(x => foo(x))
//          .traverse(FreeApplicative.lift)
//      )
//
//      _ <- Free.liftF(FreeApplicative.lift(baz()))
//      _ <- Free.liftF(FreeApplicative.lift(qux()))
//    } yield ()
//  }
//
//  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = {
//    import MyAlgebra._
//    import zio.interop.catz._
//
//    program
//      .foldMap(ParallelInterpreter(interpret)(ParallelInterpreter.zioApplicative))
//      .map(_ => 0)
//  }
//}
