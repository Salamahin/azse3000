package com.aswatson.aswrdm.azse3000.program

import com.aswatson.aswrdm.azse3000.shared._

sealed trait PromptOperation[T]
final case class PromptCommand()                            extends PromptOperation[Command]
final case class PromptCreds(acc: Account, cont: Container) extends PromptOperation[Secret]

sealed trait AnalyseCommandOperation[T]
final case class ExtractPaths(cmd: Command)    extends AnalyseCommandOperation[Seq[Path]]
final case class Desugar(cmd: Command)         extends AnalyseCommandOperation[Command]
final case class BuildExpression(cmd: Command) extends AnalyseCommandOperation[Expression[Path]]

sealed trait EndpointOperation[T]
final case class ParsePath(p: Path)                        extends EndpointOperation[ParsedPath]
final case class PrepareExpression(expr: Expression[Path]) extends EndpointOperation[Expression[ParsedPath]]

sealed trait AzureOperation[T]
final case class RunOperations(expr: Expression[ParsedPath]) extends AzureOperation[Map[OperationDescription, OperationResult]]



