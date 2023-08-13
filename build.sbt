ThisBuild / organization := "com.github.cjmx"
ThisBuild / organizationName := "cjmx"
ThisBuild / startYear := Some(2012)
ThisBuild / tlBaseVersion := "3.0"
ThisBuild / developers ++= List(
  tlGitHubDev("mpilquist", "Michael Pilquist")
)
ThisBuild / tlSonatypeUseLegacyHost := true
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("17"))

ThisBuild / scalaVersion := crossScalaVersions.value.last
ThisBuild / crossScalaVersions := Seq("2.12.10")
ThisBuild / scalacOptions ++= Seq(
  "-feature",
  "-deprecation",
  "-unchecked",
  "-Xcheckinit",
  "-Xlint",
  "-Xverify",
  "-Yno-adapted-args",
  "-Ywarn-unused-import",
  "-target:jvm-1.8"
)

ThisBuild / licenses := List(
  ("BSD-3-Clause", url("https://github.com/cjmx/cjmx/blob/main/LICENSE"))
)

name := "cjmx"

Compile / unmanagedResources ++= {
  val base = baseDirectory.value
  (base / "NOTICE") +: (base / "LICENSE") +: ((base / "licenses") * "LICENSE_*").get
}

libraryDependencies ++=
  "com.github.cjmx" % "cjmx-ext" % "1.0.0.RELEASE" ::
  "org.scala-sbt" %% "completion" % "1.0.4" ::
  "com.google.code.gson" % "gson" % "2.2.2" ::
  "org.scalatest" %% "scalatest" % "3.0.0" % "test" ::
  Nil

Proguard / proguardVersion := "5.2.1"
Proguard / proguardOptions ++= Seq(ProguardOptions.keepMain("cjmx.Main"),
  "-dontobfuscate",
  "-dontoptimize",
  "-ignorewarnings",
  "-keepparameternames",
  "-keepattributes *")
Proguard / proguardInputFilter := { file =>
  file.name match {
    case f if f startsWith "jansi-" => Some("!**")
    case "tools.jar" => Some("!META-INF/**")
    case _ => Some("!META-INF/MANIFEST.MF")
  }
}

Proguard / proguard / javaOptions := Seq("-Xmx2048m")

lazy val proguardOutputJar: TaskKey[File] = TaskKey[File]("proguard-output-jar")
proguardOutputJar := {
  val _ = (Proguard / proguard).value
  (Proguard / proguardOutputs).value.head
}

addArtifact(Artifact("cjmx", "app"), proguardOutputJar).settings

