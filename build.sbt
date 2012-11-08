seq(conscriptSettings :_*)

organization := "com.github.cjmx"

name := "cjmx"

version := "1.0.0-SNAPSHOT"

scalaVersion := "2.9.2"

scalacOptions += "-deprecation"

licenses += ("Three-clause BSD-style", url("http://github.com/cjmx/cjmx/blob/master/LICENSE"))

unmanagedResources in Compile <++= baseDirectory map { base => (base / "NOTICE") +: (base / "LICENSE") +: ((base / "licenses") * "LICENSE_*").get }

triggeredMessage := (_ => Watched.clearScreen)

resolvers += "Typesafe" at "http://repo.typesafe.com/typesafe/repo"

//resolvers += "Sonatype OSS" at "https://oss.sonatype.org/content/repositories/snapshots"

// SBT 0.12.0 is only available in the Ivy Releases repository
resolvers += Resolver.url("Typesafe Ivy Releases", url("http://repo.typesafe.com/typesafe/repo"))(Resolver.ivyStylePatterns)

libraryDependencies ++=
  "org.scalaz" %% "scalaz-core" % "7.0.0-M4" ::
  "org.scalaz" %% "scalaz-effect" % "7.0.0-M4" ::
  "org.scalaz" %% "scalaz-iteratee" % "7.0.0-M4" ::
  "org.scala-sbt" % "completion" % "0.12.0" ::
  "com.google.code.gson" % "gson" % "2.2.2" ::
  "org.scalatest" % "scalatest_2.9.0" % "1.8" % "test" ::
  Nil

unmanagedClasspath in Compile ++= toolsJar

proguardSettings

minJarPath <<= target / "cjmx.jar"

proguardOptions ++= Seq(keepMain("cjmx.Main"),
  "-dontobfuscate",
  "-dontoptimize",
  "-keepattributes Exceptions,InnerClasses,Signature,Deprecated,SourceFile,LineNumberTable,*Annotation*,EnclosingMethod")

proguardLibraryJars ++= toolsJar

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
