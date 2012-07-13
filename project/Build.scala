import sbt._
import Keys._
import ProguardPlugin._


object CjmxBuild extends Build {

  val toolsJar = Option(Path(Path.fileProperty("java.home").asFile.getParent) / "lib" / "tools.jar").filter { _.exists }.toSeq

  lazy val proguardOutputJar = TaskKey[File]("proguard-output-jar")

  lazy val proguardPublishSettings: Seq[Project.Setting[_]] = Seq(
    proguardOutputJar <<= (proguard, minJarPath) map { (_: Unit, jar: File) => jar }
  ) ++ addArtifact(Artifact("cjmx", "app"), proguardOutputJar).settings

  lazy val root = Project(id = "cjmx", base = file("."), settings = Defaults.defaultSettings ++ proguardPublishSettings)

}

