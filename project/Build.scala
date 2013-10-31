import sbt._
import Keys._
import ProguardPlugin._


object CjmxBuild extends Build {
  override def projects = Seq(root)

  val toolsJar = {
    val javaHome = Path(Path.fileProperty("java.home").asFile.getParent)
    val toolsJar = Option(javaHome / "lib" / "tools.jar").filter { _.exists }
    if (toolsJar.isEmpty && !appleJdk6OrPrior) sys.error("tools.jar not in $JAVA_HOME/lib")
    toolsJar.toSeq
  }

  def appleJdk6OrPrior: Boolean = {
    (sys.props("java.vendor") contains "Apple") && {
      val JavaVersion = """^(\d+)\.(\d+)\..*$""".r
      val JavaVersion(major, minor) = sys.props("java.version")
      major.toInt == 1 && minor.toInt < 7
    }
  }

  lazy val proguardOutputJar = TaskKey[File]("proguard-output-jar")

  lazy val proguardPublishSettings: Seq[Project.Setting[_]] = Seq(
    proguardOutputJar <<= (proguard, minJarPath) map { (_: Unit, jar: File) => jar }
  ) ++ addArtifact(Artifact("cjmx", "app"), proguardOutputJar).settings

  lazy val root = Project(id = "cjmx", base = file("."), settings = Defaults.defaultSettings ++ proguardPublishSettings)
}

