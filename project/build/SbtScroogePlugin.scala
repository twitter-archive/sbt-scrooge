import sbt._
import com.twitter.sbt._

class SbtScroogePlugin(info: ProjectInfo)
  extends PluginProject(info)
  with StandardManagedProject
  with DefaultRepos
  with SubversionPublisher
{
  override def disableCrossPaths = true

  override def subversionRepository = Some("https://svn.twitter.biz/maven-public")
  override def managedStyle = ManagedStyle.Maven
  def snapshotDeployRepo = "libs-snapshots-local"
  def releaseDeployRepo = "libs-releases-local"

  override def pomExtra =
    <licenses>
      <license>
        <name>Apache 2</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
        <distribution>repo</distribution>
      </license>
    </licenses>
}
