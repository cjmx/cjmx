import AssemblyKeys._

name := "JMX Command Line Client"

version := "1.0"

scalaVersion := "2.9.2"

licenses += ("Three-clause BSD-style license", url("http://github.com/mpilquist/cjmx/blob/master/LICENSE"))

unmanagedResources in Compile <++= baseDirectory map { base => (base / "NOTICE") +: (base / "LICENSE") +: ((base / "licenses") * "LICENSE_*").get }

resolvers += "Typesafe" at "http://repo.typesafe.com/typesafe/repo"

//resolvers += "Sonatype OSS" at "https://oss.sonatype.org/content/repositories/snapshots"

// SBT 0.12.0-RC2 is only available in the Ivy Releases repository
resolvers += Resolver.url("Typesafe Ivy Releases", url("http://repo.typesafe.com/typesafe/repo"))(Resolver.ivyStylePatterns)

libraryDependencies ++=
  "org.scalaz" %% "scalaz-core" % "7.0-SNAPSHOT" ::
  "org.scalaz" %% "scalaz-effect" % "7.0-SNAPSHOT" ::
  "org.scalaz" %% "scalaz-iteratee" % "7.0-SNAPSHOT" ::
  "org.scala-sbt" % "completion" % "0.12.0-RC2" ::
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

