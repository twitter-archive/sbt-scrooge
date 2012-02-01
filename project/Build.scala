import sbt._
import Keys._

import com.twitter.sbt._

object SbtScroogePlugin extends Build {
  lazy val root = Project(id = "sbt-scrooge2",
                          base = file("."))
  .settings(StandardProject.newSettings: _*)
  .settings(SubversionPublisher.newSettings: _*)
  .settings(
    organization := "com.twitter",
    name := "sbt-scrooge2",
    SubversionPublisher.subversionRepository := Some("https://svn.twitter.biz/maven-public"),
    version := "0.0.1-SNAPSHOT",
    sbtPlugin := true
  )
  .settings(ScriptedPlugin.scriptedSettings: _*)
}
