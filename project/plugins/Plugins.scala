import sbt._

class Plugins(info: ProjectInfo) extends PluginDefinition(info) {
  val stdProjectRepo = "std-project-repo" at "http://maven.twttr.com/"
  val stdProject = "com.twitter" % "standard-project" % "0.11.9"

  val sbtIdeaRepo = "sbt-idea-repo" at "http://mpeltonen.github.com/maven/"
  val sbtIdea = "com.github.mpeltonen" % "sbt-idea-plugin" % "0.3.0"
}
