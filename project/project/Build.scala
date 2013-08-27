import sbt._
object PluginDef extends Build {
  override lazy val projects = Seq(root)

  lazy val root = Project(id = "plugins", base = file(".")) dependsOn (proguard, conscript)

  lazy val proguard = uri("git://github.com/jsuereth/xsbt-proguard-plugin.git#sbt-0.12")

  lazy val conscript = uri("https://github.com/cjmx/conscript-plugin.git#f882a0cd12e4dc5254fd38aadfb352dfdca1edf6")
}
