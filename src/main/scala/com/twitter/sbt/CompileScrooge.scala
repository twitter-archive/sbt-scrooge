package com.twitter.sbt

import _root_.sbt._
import java.io.{File, FileOutputStream, InputStream, OutputStream}
import java.net.URL
import scala.collection.jcl

/**
 * This trait can be used when mixing in with CompileThrift* from sbt-thrift.
 * It leaves certain values undefined which are defined by CompileThrift.
 */
trait CompileThriftScroogeMixin extends DefaultProject {
  /**
   * Override this to use a different version of scrooge for code generation.
   */
  def scroogeVersion = "2.3.1"

  /**
   * Override these to turn on/off generating ostrich or finagle bindings.
   */
  def scroogeBuildOptions = List("--finagle", "--ostrich")

  /**
   * Turn me on to see the scrooge command-line (and possibly other debug info).
   */
  def scroogeDebug = false

  def scroogeName = "scrooge-" + scroogeVersion
  def scroogeCacheFolder = ("project" / "build" / "target" / scroogeName) ##
  def scroogeJar = scroogeCacheFolder / (scroogeName + ".jar")

  def generatedScalaDirectoryName: String
  def generatedScalaPath: Path

  def thriftSources: PathFinder
  def thriftIncludeFolders: Seq[String]

  def thriftNamespaceMap: Map[String, String] = Map()

  override def mainSourceRoots = super.mainSourceRoots +++ (outputPath / generatedScalaDirectoryName ##)

  def scroogeBin: String = {
    if (!scroogeJar.asFile.exists) {
      log.info("Fetching scrooge " + scroogeVersion + " ...")

      val environment = jcl.Map(System.getenv())
      val repoUrl = environment.get("SBT_PROXY_REPO") getOrElse {
        if (environment.get("SBT_TWITTER").isDefined) {
          // backward compatibility: twitter's internal proxy
          "http://artifactory.twitter.biz/repo/"
        } else {
          "http://maven.twttr.com/"
        }
      }
      val fetchUrl = repoUrl + "/com/twitter/scrooge/" + scroogeVersion + "/scrooge-" + scroogeVersion + ".zip"
      if (scroogeDebug) log.info("Fetching from: " + fetchUrl)

      scroogeCacheFolder.asFile.mkdirs()
      FileUtilities.unzip(new URL(fetchUrl).openStream, scroogeCacheFolder, log)
      FileUtilities.clean(generatedScalaPath, log)
    }

    scroogeJar.asFile.getAbsolutePath
  }

  def scroogeTask(
    thriftFiles: Iterable[String],
    includeFolders: Iterable[String],
    targetDir: File
  ) = {
    task {
      val sourcePaths = thriftFiles.mkString(" ")

      if (sourcePaths == "") {
        None
      } else {
        import Process._

        targetDir.mkdirs()

        val thriftIncludes = includeFolders.map { folder =>
          "-i " + new File(folder).getAbsolutePath
        }.mkString(" ")

        val namespaceMappings = thriftNamespaceMap.map { case (k, v) =>
          "-n " + k + "=" + v
        }.mkString(" ")

        val flags = List("--verbose") ++ scroogeBuildOptions
        val cmd = "java -jar %s %s %s %s -d %s -s %s".format(
          scroogeBin, flags.mkString(" "), thriftIncludes, namespaceMappings,
          targetDir.getAbsolutePath, sourcePaths)
        if (scroogeDebug) log.info(cmd)
        execTask(cmd).run
      }
    }
  }

  // thrift generation.
  def compileThriftScroogeAction = scroogeTask(
    thriftSources.getPaths,
    thriftIncludeFolders,
    generatedScalaPath.asFile
  ) describedAs("Compile thrift into scala")

  lazy val compileThriftScrooge = compileThriftScroogeAction

  override def compileAction = super.compileAction dependsOn(compileThriftScrooge)

  /**
   * Avoid the spinning ~compile
   */
  override def watchPaths = super.watchPaths.filter { path =>
    !path.asFile.getAbsolutePath.contains("target/gen-")
  }
}

/**
 * This trait defines default values for all properties.
 */
trait CompileThriftScrooge extends CompileThriftScroogeMixin {
  def generatedScalaDirectoryName = "gen-scala"
  def generatedScalaPath = (outputPath / generatedScalaDirectoryName) ##

  override def mainSourceRoots = super.mainSourceRoots +++ generatedScalaPath

  lazy val cleanGenerated = (
    cleanTask(generatedScalaPath)
  ) describedAs "Clean generated source folders"

  override def cleanAction = super.cleanAction dependsOn(cleanGenerated)

  def thriftSources = (mainSourcePath / "thrift" ##) ** "*.thrift"
  def thriftIncludeFolders: Seq[String] = Nil
}
