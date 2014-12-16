import sbtrelease._
import ReleaseStateTransformations._
import ReleasePlugin._
import ReleaseKeys._

seq(conscriptSettings :_*)

organization := "com.github.cjmx"

name := "cjmx"

scalaVersion := "2.10.4"

crossScalaVersions := Seq("2.10.4", "2.11.4")

scalacOptions ++= Seq(
  "-feature",
  "-deprecation",
  "-unchecked",
//  "-optimise", // this seems to be triggering this bug - https://issues.scala-lang.org/browse/SI-3882
  "-Xcheckinit",
  "-Xlint",
  "-Xverify",
  "-Yclosure-elim",
  "-Yno-adapted-args",
  "-target:jvm-1.6"
)

licenses += ("Three-clause BSD-style", url("http://github.com/cjmx/cjmx/blob/master/LICENSE"))

unmanagedResources in Compile <++= baseDirectory map { base => (base / "NOTICE") +: (base / "LICENSE") +: ((base / "licenses") * "LICENSE_*").get }

triggeredMessage := (_ => Watched.clearScreen)

// SBT is only available in the Ivy Releases repository
resolvers += Resolver.url("Typesafe Ivy Releases", url("http://repo.typesafe.com/typesafe/repo"))(Resolver.ivyStylePatterns)

// Scalaz-stream is available from this repo
resolvers += "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases"

libraryDependencies ++=
  "com.github.cjmx" % "cjmx-ext" % "1.0.0.RELEASE" ::
  "org.scalaz" %% "scalaz-core" % "7.1.0" ::
  "org.scalaz" %% "scalaz-effect" % "7.1.0" ::
  "org.scala-sbt" % "completion" % (scalaBinaryVersion.value match { case "2.10" => "0.13.5"; case "2.11" => "0.13.7" }) ::
  "com.google.code.gson" % "gson" % "2.2.2" ::
  "org.scalatest" %% "scalatest" % "2.2.1" % "test" ::
  "org.scalaz.stream" %% "scalaz-stream" % "0.6a" ::
  Nil

unmanagedClasspath in Compile ++= toolsJar

unmanagedClasspath in Test ++= toolsJar

proguardSettings

ProguardKeys.options in Proguard ++= Seq(ProguardOptions.keepMain("cjmx.Main"),
  "-dontobfuscate",
  "-dontoptimize",
  "-dontnote",
  "-dontwarn",
  "-ignorewarnings",
  "-keepattributes Exceptions,InnerClasses,Signature,Deprecated,SourceFile,LineNumberTable,*Annotation*,EnclosingMethod")

javaOptions in (Proguard, ProguardKeys.proguard) := Seq("-Xmx2048m", "-XX:MaxPermSize=512m", "-XX:ReservedCodeCacheSize=256m")

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

pomPostProcess := { (node) =>
  import scala.xml._
  import scala.xml.transform._
  def stripIf(f: Node => Boolean) = new RewriteRule {
    override def transform(n: Node) =
      if (f(n)) NodeSeq.Empty else n
  }
  val stripSnapshots = stripIf { n => n.label == "dependency" && (n \ "version").text.endsWith("-SNAPSHOT") }
  val stripTestScope = stripIf { n => n.label == "dependency" && (n \ "scope").text == "test" }
  val stripConscriptDependencies = stripIf { n => n.label == "dependency" && Set("xsbt", "launcher-interface").contains((n \ "artifactId").text) }
  new RuleTransformer(stripSnapshots, stripTestScope, stripConscriptDependencies).transform(node)(0)
}

releaseSettings

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  publishArtifacts.copy(action = publishSignedAction),
  setNextVersion,
  commitNextVersion,
  pushChanges
)
