name := "azse3000"
version := "0.1"

scalaVersion := "2.13.2"

val catsV = "2.2.0-M2"
val zioV  = "1.0.0-RC17"

scalacOptions ++= Seq(
  "-Xfatal-warnings",
  "-deprecation",
  "-language:postfixOps",
  "-language:higherKinds"
)

libraryDependencies ++= Seq(
  "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.2",
  "org.typelevel"          %% "cats-core"                % catsV,
  "org.typelevel"          %% "cats-free"                % catsV,
  "org.typelevel"          %% "cats-effect"              % "2.1.3",
  "dev.zio"                %% "zio"                      % zioV,
  "dev.zio"                %% "zio-interop-cats"         % "2.0.0.0-RC10",
  "com.github.pureconfig"  %% "pureconfig"               % "0.12.3",
  "com.microsoft.azure"     % "azure-storage"            % "8.6.0",
  "org.jline"               % "jline"                    % "3.14.1",
  "org.fusesource.jansi"    % "jansi"                    % "1.18"
)

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest"                      % "3.1.1"  % Test,
  "dev.zio"       %% "zio-test"                       % zioV     % Test,
  "com.dimafeng"  %% "testcontainers-scala-scalatest" % "0.36.1" % Test
)

addCompilerPlugin("org.typelevel" %% "kind-projector"     % "0.11.0" cross CrossVersion.full)
addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % "0.3.1")
