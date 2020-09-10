name := "blindsend"
version := "0.0.1"

scalaVersion := "2.13.3"

resolvers += "Sonatype Public" at "https://oss.sonatype.org/content/groups/public/"
resolvers += "JCenter" at "https://jcenter.bintray.com/"

scalacOptions ++= Seq(
  "-feature",
  "-deprecation",
  "-unchecked",
  "-language:postfixOps",
  "-language:higherKinds",
  "-Ywarn-unused:_,imports",
  "-Ywarn-unused:imports"
)

val CatsVersion   = "2.1.1"
val CirceVersion  = "0.13.0"
val TsecVersion   = "0.2.1"
val http4sVersion = "0.21.7"

libraryDependencies ++= Seq(
  "org.typelevel"         %% "cats-core"           % CatsVersion,
  "org.typelevel"         %% "cats-effect"         % CatsVersion,
  "io.circe"              %% "circe-core"          % CirceVersion,
  "io.circe"              %% "circe-parser"        % CirceVersion,
  "io.circe"              %% "circe-generic"       % CirceVersion,
  "com.github.pureconfig" %% "pureconfig"          % "0.12.2",
  "org.http4s"            %% "http4s-dsl"          % http4sVersion,
  "org.http4s"            %% "http4s-server"       % http4sVersion,
  "org.http4s"            %% "http4s-circe"        % http4sVersion,
  "org.http4s"            %% "http4s-blaze-server" % http4sVersion,
  "org.http4s"            %% "http4s-blaze-client" % http4sVersion,
  "org.bouncycastle"      % "bcprov-jdk15to18"     % "1.66",
  "com.google.cloud"      % "google-cloud-storage" % "1.111.2",
  "ch.qos.logback"        % "logback-classic"      % "1.2.3",
  "org.codehaus.janino"   % "janino"               % "2.6.1",
  "org.tpolecat"          %% "skunk-core"          % "0.0.18",
  "org.tpolecat"          %% "doobie-core"         % "0.9.0",
  "org.tpolecat"          %% "doobie-postgres"     % "0.9.0",
  "org.tpolecat"          %% "doobie-h2"           % "0.9.0",
  "org.tpolecat"          %% "doobie-hikari"       % "0.9.0"
)

addCompilerPlugin("org.typelevel" %% "kind-projector"     % "0.11.0" cross CrossVersion.full)
addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % "0.3.1")

addCommandAlias("fix", "all compile:scalafix test:scalafix")

scalafixDependencies in ThisBuild += "com.nequissimus" %% "sort-imports" % "0.3.2"

assemblyMergeStrategy in assembly := {
  case x if Assembly.isConfigFile(x) =>
    MergeStrategy.concat
  case PathList(ps @ _*) if Assembly.isReadme(ps.last) || Assembly.isLicenseFile(ps.last) =>
    MergeStrategy.rename
  case PathList("META-INF", xs @ _*) =>
    (xs map { _.toLowerCase }) match {
      case ("manifest.mf" :: Nil) | ("index.list" :: Nil) | ("dependencies" :: Nil) =>
        MergeStrategy.discard
      case ps @ (x :: xs) if ps.last.endsWith(".sf") || ps.last.endsWith(".dsa") =>
        MergeStrategy.discard
      case "services" :: xs =>
        MergeStrategy.filterDistinctLines
      case ("io.netty.versions.properties" :: Nil) =>
        MergeStrategy.concat
      case _ => MergeStrategy.deduplicate
    }
  case "module-info.class" => MergeStrategy.filterDistinctLines
  case _                   => MergeStrategy.deduplicate
}

mainClass in assembly := Some("io.blindsend.app.Main")
assemblyJarName in assembly := "blindsend.jar"
