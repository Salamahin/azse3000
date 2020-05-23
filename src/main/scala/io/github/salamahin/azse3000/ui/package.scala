package io.github.salamahin.azse3000
import cats.InjectK
import io.github.salamahin.azse3000.shared._

package object ui {

  sealed trait UIOps[T]

  final case class PromptCommand()                                                 extends UIOps[Command]
  final case class PromptCreds(acc: Account, cont: Container)                      extends UIOps[Secret]
  final case class ShowProgress(op: Description, progress: Int, complete: Boolean) extends UIOps[Unit]
  final case class ShowReports(reports: Vector[InterpretationReport])              extends UIOps[Unit]

  final class UserInterface[F[_]]()(implicit inj: InjectK[UIOps, F]) {
    def promptCommand()                                                 = inj(PromptCommand())
    def promptCreds(acc: Account, cont: Container)                      = inj(PromptCreds(acc, cont))
    def showProgress(op: Description, progress: Int, complete: Boolean) = inj(ShowProgress(op, progress, complete))
    def showReports(reports: Vector[InterpretationReport])              = inj(ShowReports(reports))
  }

  object UserInterface {
    implicit def userInterface[F[_]](implicit I: InjectK[UIOps, F]) = new UserInterface[F]
  }
}
