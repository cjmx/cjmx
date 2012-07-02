import sbt._
import Keys._

object CjmxBuild extends Build {

  val toolsJar = Option(Path(Path.fileProperty("java.home").asFile.getParent) / "lib" / "tools.jar").filter { _.exists }.toSeq

  lazy val root = Project(id = "cjmx", base = file("."))

}

