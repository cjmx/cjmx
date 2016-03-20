import sbtrelease._
import ReleaseStateTransformations._
import ReleasePlugin._
import ReleaseKeys._

organization := "com.github.cjmx"

name := "cjmx"

scalaVersion := "2.11.8"

crossScalaVersions := Seq("2.11.8")

scalacOptions ++= Seq(
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

licenses += ("Three-clause BSD-style", url("http://github.com/cjmx/cjmx/blob/master/LICENSE"))

unmanagedResources in Compile <++= baseDirectory map { base => (base / "NOTICE") +: (base / "LICENSE") +: ((base / "licenses") * "LICENSE_*").get }

triggeredMessage := (_ => Watched.clearScreen)

// SBT is only available in the Ivy Releases repository
resolvers += Resolver.url("Typesafe Ivy Releases", url("http://repo.typesafe.com/typesafe/repo"))(Resolver.ivyStylePatterns)

libraryDependencies ++=
  "com.github.cjmx" % "cjmx-ext" % "1.0.0.RELEASE" ::
  "org.scala-sbt" % "completion" % "0.13.11" ::
  "com.google.code.gson" % "gson" % "2.2.2" ::
  "org.scalatest" %% "scalatest" % "2.2.1" % "test" ::
  Nil

unmanagedClasspath in Compile ++= toolsJar
unmanagedClasspath in Runtime ++= toolsJar
unmanagedClasspath in Test ++= toolsJar

proguardSettings
ProguardKeys.proguardVersion in Proguard := "5.2.1"
ProguardKeys.options in Proguard ++= Seq(ProguardOptions.keepMain("cjmx.Main"),
  "-dontobfuscate",
  "-dontoptimize",
  "-ignorewarnings",
  "-keepparameternames",
  "-keepattributes *")
ProguardKeys.inputFilter in Proguard := { file =>
  file.name match {
    case f if f startsWith "jansi-" => Some("!**")
    case _ => Some("!META-INF/MANIFEST.MF")
  }
}

javaOptions in (Proguard, ProguardKeys.proguard) := Seq("-Xmx2048m")

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
  new RuleTransformer(stripSnapshots, stripTestScope).transform(node)(0)
}

releasePublishArtifactsAction := PgpKeys.publishSigned.value

