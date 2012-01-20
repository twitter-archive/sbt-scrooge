organization := "com.twitter"

name := "sbt-scrooge"

version := "11.0.0-SNAPSHOT"

sbtPlugin := true

seq(ScriptedPlugin.scriptedSettings: _*)

scriptedLaunchOpts ++= Seq("-XX:+CMSClassUnloadingEnabled", "-XX:MaxPermSize=2024m")
