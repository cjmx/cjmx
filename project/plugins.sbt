resolvers += Resolver.url("artifactory", url("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases"))(Resolver.ivyStylePatterns)

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.8.3")

libraryDependencies <+= sbtVersion(v => "com.github.siasia" %% "xsbt-proguard-plugin" % ("0.11.2-0.1.2"))

//libraryDependencies += Defaults.sbtPluginExtra("com.eed3si9n" % "sbt-assembly" % "0.8.3", "0.12.0-Beta2", "2.9.2")
