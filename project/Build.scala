import sbt._
import Keys._
import ProguardPlugin._


object CjmxBuild extends Build {
  override def projects = Seq(root)

  val toolsJar = {
    val r: Option[File] = Option(Path(Path.fileProperty("java.home").asFile.getParent) / "lib" / "tools.jar").filter { _.exists }
    if (!r.isDefined) {
      sys.error("'java.home' not defined; unable to locate tools.jar")
    }
    r.toSeq
  }

  lazy val proguardOutputJar = TaskKey[File]("proguard-output-jar")

  lazy val proguardPublishSettings: Seq[Project.Setting[_]] = Seq(
    proguardOutputJar <<= (proguard, minJarPath) map { (_: Unit, jar: File) => jar }
  ) ++ addArtifact(Artifact("cjmx", "app"), proguardOutputJar).settings

  lazy val root = Project(id = "cjmx", base = file("."), settings = Defaults.defaultSettings ++ proguardPublishSettings)
}

