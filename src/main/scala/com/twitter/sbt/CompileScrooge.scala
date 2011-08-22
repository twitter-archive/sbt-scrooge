package com.twitter.sbt

import _root_.sbt._
import _root_.xsbt.FileUtilities
import java.io.{File, FileOutputStream, InputStream, OutputStream}

object CompileThriftScrooge {
  val ScroogeVersion = "1.1.0"
  private[sbt] var cachedScroogeJarPath: Option[String] = None
}

trait CompileThriftScrooge extends DefaultProject {
  import CompileThriftScrooge._

  def generatedScalaDirectoryName = "gen-scala"
  def generatedScalaPath = outputPath / generatedScalaDirectoryName

  override def mainSourceRoots = super.mainSourceRoots +++ (outputPath / generatedScalaDirectoryName ##)

  lazy val cleanGenerated = (
    cleanTask(generatedScalaPath)
  ) describedAs "Clean generated source folders"

  override def cleanAction = super.cleanAction dependsOn(cleanGenerated)

  def thriftSources = (mainSourcePath / "thrift" ##) ** "*.thrift"
  def thriftIncludeFolders: Seq[String] = Nil

  private[this] lazy val _scroogeBin = CompileThriftScrooge.synchronized {
    if (!cachedScroogeJarPath.isDefined) {
      // TODO: we don't discriminate between versions here (which we need to..).
      val stream = getClass.getResourceAsStream("/scrooge.zip")
      val zipFile = File.createTempFile("scrooge", ".zip")
      zipFile.deleteOnExit()
      val fos = new FileOutputStream(zipFile)
      try {
        val buf = new Array[Byte](4096)
        def copy(out: OutputStream, in: InputStream) {
          val len = in.read(buf)
          if (len > 0) {
            out.write(buf, 0, len)
            copy(out, in)
          }
        }
        copy(fos, stream)
      } finally {
        fos.close()
      }

      val scroogeDir = File.createTempFile("scrooge", "dir")
      scroogeDir.delete()
      scroogeDir.mkdir()
      scroogeDir.deleteOnExit()

      FileUtilities.unzip(zipFile, scroogeDir)

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
      import Process._

      targetDir.mkdirs()

      val thriftIncludes = includeFolders.map { folder =>
        new File(folder).getAbsolutePath
      }.mkString(File.pathSeparator) match {
        case "" => ""
        case includes => "-i " + includes + " "
      }

      val sourcePaths = thriftFiles.mkString(" ")
      val cmd = "java -jar %s %s -d %s %s".format(scroogeBin, thriftIncludes, targetDir.getAbsolutePath, sourcePaths)
      Console.println(cmd)
      execTask(cmd).run
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
}
