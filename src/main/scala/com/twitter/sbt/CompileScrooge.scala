package com.twitter.sbt
import scala.collection.mutable.Map
import scala.collection.JavaConversions._

import sbt._
import Keys._


/**
 * An SBT 0.11 plugin for generating thrift with scrooge
 */
object CompileThriftScrooge extends Plugin {

  // a bunch of keys for downloading a scrooge release
  /**
   * which version of scrooge to use
   */
  val scroogeVersion = SettingKey[String]("scrooge-version", "version of scrooge to download and use")
  /**
   * whether to output debug logging
   */
  val scroogeDebug = SettingKey[Boolean]("scrooge-debug", "run scrooge in debug mode")
  /**
   * scrooge's version qualified name
   */
  val scroogeName = SettingKey[String]("scrooge-name", "scrooge's version qualified name (scrooge + '-' + scroogeVersion)")
  /**
   * the folder to unpack scrooge to
   */
  val scroogeCacheFolder = SettingKey[File]("scrooge-cache-folder", "where to unpack downloaded scrooge")
  /**
   * scrooge jar file
   */
  val scroogeJar = SettingKey[File]("scrooge-jar", "the local scrooge jar file")
  /**
   * get a scrooge zip, unpack it
   */
  val scroogeFetch = TaskKey[Option[File]]("scrooge-fetch", "fetch the scrooge zip package and unpack it to scrooge-cache-folder")

  // keys used for actual scrooge generation
  /**
   * command line args to pass to scrooge
   */
  val scroogeBuildOptions = SettingKey[Seq[String]]("scrooge-build-options", "command line args to pass to scrooge")
  /**
   * folders to look in for include thrift files
   */
  val scroogeThriftIncludeFolders = SettingKey[Seq[File]]("scrooge-thrift-include-folders", "folders to use in thrift includes")
  /**
   * map of thrift namespaces -> scala packages
   */
  val scroogeThriftNamespaceMap = SettingKey[Map[String, String]]("scrooge-thrift-namespace-map", "namespace rewriting, to support generationg of java/finagle/scrooge to the same jar")
  /**
   * directories to look in for thrift sources
   */
  val scroogeThriftSourceDir = SettingKey[File]("scrooge-thrift-source-dir", "directory containing thrift files")
  /**
   * thrift sources files (thriftSourceDir / ** (*.thrift))
   */
  val scroogeThriftSources = SettingKey[Seq[File]]("scrooge-thrift-sources", "thrift sources to compile")
  /**
   * where to spit out generated thrift. Note that this doesn *not* (by default) tack "scala" on the end
   */
  val scroogeThriftOutputDir = SettingKey[File]("scrooge-thrift-output-dir", "Directory where the scala files should be placed. Defaults to sourceManaged")
  /**
   * do we need to generate stuff
   */
  val scroogeIsDirty = TaskKey[Boolean]("scrooge-is-dirty", "do we need to regenerate")
  /**
   * the actual task to generate thrift
   */
  val scroogeGen = TaskKey[Seq[File]]("scrooge-gen", "generate thrift files using scrooge")
  
  val newSettings = Seq(
    scroogeVersion := "2.4.0",
    scroogeBuildOptions := Seq("--finagle", "--ostrich"),
    scroogeDebug := false,
    scroogeName <<= (scroogeVersion) { ver => "scrooge-%s".format(ver) },
    scroogeCacheFolder <<= (baseDirectory, scroogeName) { (base, scrooge) => base / "project" / "target" / scrooge },
    scroogeJar <<= (scroogeCacheFolder, scroogeName) { (folder, name) => folder / (name + ".jar")  },
    scroogeFetch <<= (streams, scroogeCacheFolder, scroogeJar, scroogeVersion, scroogeDebug) map { (out, cacheFolder, jar, ver, debug) =>
      if (!jar.exists) {
        out.log.info("Fetching scrooge " + ver + " ...")

        val environment: Map[String, String] = System.getenv()
        val repoUrl = environment.get("SBT_PROXY_REPO") getOrElse {
          if (environment.get("SBT_TWITTER").isDefined) {
            // backward compatibility: twitter's internal proxy
            "http://artifactory.twitter.biz/repo/"
          } else {
            "http://maven.twttr.com/"
          }
        }
        val fetchUrl = repoUrl + "/com/twitter/scrooge/" + ver + "/scrooge-" + ver + ".zip"
        if (debug) out.log.info("Fetching from: " + fetchUrl)

        cacheFolder.asFile.mkdirs()
        IO.unzipURL(new URL(fetchUrl), cacheFolder)
        if (jar.exists) {
          Some(jar)
        } else {
          None
        }
      } else {
        None
      }
    },
    scroogeThriftSourceDir <<= (sourceDirectory in Compile) { _ / "thrift" },
    scroogeThriftSources <<= (scroogeThriftSourceDir) { srcDir => (srcDir ** "*.thrift").get },
    scroogeThriftOutputDir <<= (sourceManaged in Compile) { _ / "scala" },
    scroogeThriftIncludeFolders := Seq(),
    scroogeThriftNamespaceMap := Map(),
    // look at includes and our sources to see if anything is newer than any of our output files
    scroogeIsDirty <<= (streams,
                    scroogeThriftSources,
                    scroogeThriftOutputDir,
                    scroogeThriftIncludeFolders) map { (out, sources, outputDir, inc) => {
      // figure out if we need to actually rebuild, based on mtimes
      val allSourceDeps = sources ++ inc.foldLeft(Seq[File]()) { (files, dir) => files ++ (dir ** "*.thrift").get }
      val sourcesLastModified:Seq[Long] = allSourceDeps.map(_.lastModified)
      val newestSource = if (sourcesLastModified.size > 0) {
        sourcesLastModified.max
      } else {
        Long.MaxValue
      }
      val outputsLastModified = (outputDir ** "*.scala").get.map(_.lastModified)
      val oldestOutput = if (outputsLastModified.size > 0) {
        outputsLastModified.min
      } else {
        Long.MinValue
      }
      oldestOutput < newestSource
    }},
    // actually run scrooge
    scroogeGen <<= (streams,
                    scroogeIsDirty,
                    scroogeThriftSources,
                    scroogeThriftOutputDir,
                    scroogeJar,
                    scroogeBuildOptions,
                    scroogeThriftIncludeFolders,
                    scroogeThriftNamespaceMap) map { (out, isDirty, sources, outputDir, jar, opts, inc, ns) => {
      out.log.info("generating scrooge thrift for %s...".format(sources.mkString(", ")))
      outputDir.mkdirs()
      if (isDirty) {
        val sourcePaths = sources.mkString(" ")
        val namespaceMappings = ns.map { case (k, v) =>
          "-n " + k + "=" + v
        }.mkString(" ")
        val thriftIncludes = inc.map { folder =>
          "-i " + folder.getAbsolutePath
        }.mkString(" ")
        val cmd = "java -jar %s %s %s %s -d %s -s %s".format(
          jar, opts.mkString(" "), thriftIncludes, namespaceMappings,
          outputDir.getAbsolutePath, sources.mkString(" "))
        out.log.info(cmd)
        <x>{cmd}</x> !
      }
      (outputDir ** "*.scala").get.toSeq
    }},
    // register ourselves with source generators
    sourceGenerators in Compile <+= scroogeGen
  )
}
