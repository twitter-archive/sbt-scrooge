package com.twitter.sbt

import _root_.sbt._
import _root_.xsbt.{AllPassFilter, FileUtilities}
import java.io.{File, FileOutputStream, InputStream, OutputStream}

object CompileThriftScrooge {
  val ScroogeVersion = "1.1.2-SNAPSHOT"
  private[sbt] var cachedScroogeJarPath: Option[String] = None
}

/**
 * This trait can be used when mixing in with CompileThrift* from sbt-thrift.
 * It leaves certain values undefined which are defined by CompileThrift.
 */
trait CompileThriftScroogeMixin extends DefaultProject {
  import CompileThriftScrooge._

  def generatedScalaDirectoryName: String
  def generatedScalaPath: Path

  def thriftSources: PathFinder // = (mainSourcePath / "thrift" ##) ** "*.thrift"
  def thriftIncludeFolders: Seq[String] //= Nil

  def thriftNamespaceMap: Map[String, String] = Map()

  override def mainSourceRoots = super.mainSourceRoots +++ (outputPath / generatedScalaDirectoryName ##)

  private[this] lazy val _scroogeBin = CompileThriftScrooge.synchronized {
    if (!cachedScroogeJarPath.isDefined) {
      // extract scrooge.zip
      val stream = getClass.getResourceAsStream("/scrooge.zip")
      val scroogeDir = File.createTempFile("scrooge", "dir")
      scroogeDir.delete()
      scroogeDir.mkdir()

      FileUtilities.unzip(stream, scroogeDir, AllPassFilter)

      val jarFile = new File(scroogeDir, "scrooge-" + ScroogeVersion + ".jar")
      cachedScroogeJarPath = Some(jarFile.getAbsolutePath())
    }

    cachedScroogeJarPath.get
  }

  def scroogeBin = _scroogeBin

  def scroogeTask(
    thriftFiles: Iterable[String],
    includeFolders: Iterable[String],
    targetDir: File) =
  {
    task {
      val sourcePaths = thriftFiles.mkString(" ")

      if (sourcePaths == "") {
        None
      } else {
        import Process._

        targetDir.mkdirs()

        val thriftIncludes = includeFolders.map { folder =>
          new File(folder).getAbsolutePath
        }.mkString(File.pathSeparator) match {
          case "" => ""
          case includes => "-i " + includes + " "
        }

        val namespaceMappings = thriftNamespaceMap map {
          case (k, v) => k + "->" + v
        } match {
          case m if m.isEmpty => ""
          case m => "-n " + m.mkString(",")
        }

        val cmd = "java -jar %s %s %s -d %s %s".format(
          scroogeBin, thriftIncludes, namespaceMappings, targetDir.getAbsolutePath, sourcePaths)
        log.info(cmd)
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
  def generatedScalaPath = outputPath / generatedScalaDirectoryName

  override def mainSourceRoots = super.mainSourceRoots +++ (outputPath / generatedScalaDirectoryName ##)

  lazy val cleanGenerated = (
    cleanTask(generatedScalaPath)
  ) describedAs "Clean generated source folders"

  override def cleanAction = super.cleanAction dependsOn(cleanGenerated)

  def thriftSources = (mainSourcePath / "thrift" ##) ** "*.thrift"
  def thriftIncludeFolders: Seq[String] = Nil
}
