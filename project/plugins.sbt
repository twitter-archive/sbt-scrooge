resolvers += Resolver.url("Typesafe repository", new java.net.URL("http://typesafe.artifactoryonline.com/typesafe/ivy-releases/"))(Resolver.defaultIvyPatterns)

libraryDependencies <+= (sbtVersion) { sv =>
  "org.scala-tools.sbt" %% "scripted-plugin" % sv
}

resolvers += "twttr" at "http://maven.twttr.com"

addSbtPlugin("com.twitter" % "standard-project2" % "0.0.3")
