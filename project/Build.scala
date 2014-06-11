import sbt._
import Keys._
import sbtrelease._
import ReleaseStateTransformations._
import ReleasePlugin._
import ReleaseKeys._
import Utilities._
import com.typesafe.sbt.SbtPgp.PgpKeys._
import com.typesafe.sbt.SbtProguard._


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

  lazy val proguardOutputJar: TaskKey[File] = TaskKey[File]("proguard-output-jar")

  lazy val proguardPublishSettings: Seq[Project.Setting[_]] = Seq(
    proguardOutputJar <<= (ProguardKeys.proguard in Proguard, ProguardKeys.outputs in Proguard) map { (_, jars) => jars.head }
  ) ++ addArtifact(Artifact("cjmx", "app"), proguardOutputJar).settings

  lazy val root = Project(id = "cjmx", base = file("."), settings = Defaults.defaultSettings ++ proguardPublishSettings)

  lazy val publishSignedAction = { st: State =>
    val extracted = st.extract
    val ref = extracted.get(thisProjectRef)
    extracted.runAggregated(publishSigned in Global in ref, st)
  }

}

