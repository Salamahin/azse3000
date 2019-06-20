name := "azse3000"
version := "0.1"

scalaVersion := "2.12.8"

scalacOptions ++= Seq(
  "-Ypartial-unification",
  "-Xfatal-warnings",
  "-language:postfixOps",
  "-language:higherKinds"
)

libraryDependencies ++= Seq(
  "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.2",
  "org.typelevel"          %% "cats-core"                % "2.1.0",
  "dev.zio"                %% "zio"                      % "1.0.0-RC17",
  "com.nequissimus"        %% "zio-slf4j"                % "0.4.1",
  "com.microsoft.azure"    % "azure-storage"             % "8.6.0",
  "ch.qos.logback"         % "logback-classic"           % "1.2.3",
  "dev.zio"                %% "zio-interop-cats"         % "2.0.0.0-RC10",
  "com.github.pureconfig"  %% "pureconfig"               % "0.12.3"
)

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.8" % Test

addCompilerPlugin("org.typelevel" %% "kind-projector"     % "0.11.0" cross CrossVersion.full)
addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % "0.3.1")
