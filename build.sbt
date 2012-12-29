import AssemblyKeys._

assemblySettings

seq(conscriptSettings :_*)

organization := "com.github.cjmx"

name := "cjmx"

version := "1.0.0-SNAPSHOT"

scalaVersion := "2.9.2"

//crossScalaVersions := Seq("2.9.2", "2.10.0")

scalacOptions ++= Seq(
  "-deprecation",
  "-unchecked",
  "-optimise",
  "-Xcheckinit",
  "-Xlint",
  "-Xverify",
  "-Yclosure-elim",
  "-Yinline",
  "-Ywarn-all")

scalacOptions <++= scalaVersion map { sv =>
  val MajorMinor = """(\d+)\.(\d+).*""".r
  sv match {
    case MajorMinor(major, minor) if major.toInt > 2 || major == "2" && minor.toInt >= 10 =>
      Seq("-feature", "-language:_")
    case _ => Seq.empty
  }
}

licenses += ("Three-clause BSD-style", url("http://github.com/cjmx/cjmx/blob/master/LICENSE"))

unmanagedResources in Compile <++= baseDirectory map { base => (base / "NOTICE") +: (base / "LICENSE") +: ((base / "licenses") * "LICENSE_*").get }

triggeredMessage := (_ => Watched.clearScreen)

resolvers += "Typesafe" at "http://repo.typesafe.com/typesafe/repo"

// SBT 0.12.0 is only available in the Ivy Releases repository
resolvers += Resolver.url("Typesafe Ivy Releases", url("http://repo.typesafe.com/typesafe/repo"))(Resolver.ivyStylePatterns)

libraryDependencies ++=
  "org.scalaz" %% "scalaz-core" % "7.0.0-M7" ::
  "org.scalaz" %% "scalaz-effect" % "7.0.0-M7" ::
  "org.scalaz" %% "scalaz-iteratee" % "7.0.0-M7" ::
  "org.scala-sbt" % "completion" % "0.12.0" ::
  "com.google.code.gson" % "gson" % "2.2.2" ::
  "org.scalatest" %% "scalatest" % "2.0.M6-SNAP3" % "test" ::
  Nil

unmanagedClasspath in Compile ++= toolsJar

jarName in assembly := "cjmx.jar"

artifact in (Compile, assembly) ~= { art =>
  art.copy(`classifier` = Some("app"))
}

addArtifact(artifact in (Compile, assembly), assembly)

publishTo <<= version { v: String =>
  val nexus = "https://oss.sonatype.org/"
  if (v.trim.endsWith("SNAPSHOT"))
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { x => false }

pomExtra := (
  <url>http://github.com/cjmx/cjmx</url>
  <scm>
    <url>git@github.com:cjmx/cjmx.git</url>
    <connection>scm:git:git@github.com:cjmx/cjmx.git</connection>
  </scm>
  <developers>
    <developer>
      <id>mpilquist</id>
      <name>Michael Pilquist</name>
      <url>http://github.com/mpilquist</url>
    </developer>
  </developers>
)

useGpg := true
