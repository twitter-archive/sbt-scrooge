import com.twitter.sbt._

version := "0.1"

scalaVersion := "2.8.1"

seq(CompileThriftScrooge.newSettings:_*)

resolvers += "twitter" at "http://maven.twttr.com/"

libraryDependencies ++= Seq("com.twitter" % "finagle-thrift" % "1.9.12",
                            "com.twitter" % "finagle-ostrich4" % "1.9.12",
                            "com.twitter" % "util-logging" % "1.12.9",
                            "com.twitter" % "scrooge-runtime" % "1.0.4")
