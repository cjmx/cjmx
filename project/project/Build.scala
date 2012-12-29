import sbt._
object PluginDef extends Build {
  override lazy val projects = Seq(root)

  lazy val root = Project(id = "plugins", base = file(".")) dependsOn proguard

  lazy val proguard = uri("git://github.com/jsuereth/xsbt-proguard-plugin.git#sbt-0.12")
}
