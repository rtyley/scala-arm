import sbt._
import Keys._
import com.typesafe.sbt.SbtSite.site
import com.typesafe.sbt.SbtSite.SiteKeys._
import com.typesafe.sbt.site.JekyllSupport.Jekyll
import com.typesafe.sbt.SbtGhPages.ghpages
import com.typesafe.sbt.SbtGit.git
import com.typesafe.tools.mima.plugin.MimaPlugin.mimaDefaultSettings
import com.typesafe.tools.mima.plugin.MimaKeys.previousArtifact
import sbtrelease._
import sbtrelease.ReleasePlugin._
import ReleaseStateTransformations._
import xerial.sbt.Sonatype._
import SonatypeKeys._
import com.typesafe.sbt.pgp.PgpKeys

object ArmDef extends Build {

  val arm = (Project("scala-arm", file(".")) settings(
    organization := "com.madgag",
    name := "scala-arm",
    scalaVersion := "2.11.0",
    crossScalaVersions := Seq("2.10.4", "2.11.0"),
    resolvers += "java.net repo" at "http://download.java.net/maven/2/",
    libraryDependencies ++= dependencies,
    autoCompilerPlugins := true,
    addContinuations,
    scalacOptions += "-P:continuations:enable"
  ) settings(releaseSettings:_*) settings(sonatypeSettings:_*) settings(publishSettings:_*) settings(websiteSettings:_*)) settings(bcSettings:_*)

  def bcSettings: Seq[Setting[_]] = mimaDefaultSettings ++ Seq(
    previousArtifact <<= scalaVersion apply { sv =>
      if(sv startsWith "2.9") Some("com.jsuereth" % "scala-arm_2.9.1" % "1.2")
      else if(sv startsWith "2.10") Some("com.jsuereth" % "scala-arm_2.10" % "1.2")
      else None
    }
  )

  def publishSettings: Seq[Setting[_]] = Seq(
    // If we want on maven central, we need to be in maven style.
    publishMavenStyle := true,
    publishArtifact in Test := false,
    // The Nexus repo we're publishing to.
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (version.value.trim.endsWith("SNAPSHOT")) Some("snapshots" at nexus + "content/repositories/snapshots") 
      else                             Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    },
    // Maven central cannot allow other repos.  We're ok here because the artifacts we
    // we use externally are *optional* dependencies.
    pomIncludeRepository := { x => false },
    
    // Maven central wants some extra metadata to keep things 'clean'.
    homepage := Some(url("http://jsuereth.com/scala-arm")),
    licenses += "BSD-Style" -> url("http://www.opensource.org/licenses/bsd-license.php"),
    scmInfo := Some(ScmInfo(url("http://github.com/rtyley/scala-arm"), "scm:git@github.com:rtyley/scala-arm.git")),
    pomExtra := (
      <developers>
        <developer>
          <id>jsuereth</id>
          <name>Josh Suereth</name>
          <url>http://jsuereth.com</url>
        </developer>
      </developers>),
    ReleasePlugin.ReleaseKeys.releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      runTest,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      ReleaseStep(
        action = { state =>
          val extracted = Project extract state
          extracted.runAggregated(PgpKeys.publishSigned in Global in extracted.get(thisProjectRef), state)
        },
        enableCrossBuild = true
      ),
      setNextVersion,
      commitNextVersion,
      ReleaseStep{ state =>
        val extracted = Project extract state
        extracted.runAggregated(SonatypeKeys.sonatypeReleaseAll in Global in extracted.get(thisProjectRef), state)
      },
      pushChanges
    )
  )

  def websiteSettings: Seq[Setting[_]] = (
    site.settings ++ 
    ghpages.settings ++ 
    site.jekyllSupport() ++
    site.includeScaladoc() ++
    Seq(
      git.remoteRepo := "git@github.com:rtyley/scala-arm.git",
      includeFilter in Jekyll := ("*.html" | "*.png" | "*.js" | "*.css" | "CNAME")
    )
  )

  def addContinuations = libraryDependencies ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      // if scala 2.11+ is used, add dependency on scala-xml module
      case Some((2, scalaMajor)) if scalaMajor >= 11 =>
        Seq(compilerPlugin("org.scala-lang.plugins" % "scala-continuations-plugin_2.11.0" % "1.0.1"),
          "org.scala-lang.plugins" %% "scala-continuations-library" % "1.0.1")
      case _ =>
        Seq(compilerPlugin("org.scala-lang.plugins" % "continuations" % scalaVersion.value))
    }
  }

  def dependencies = Seq(
    "junit" % "junit" % "4.10" % "test",
    "com.novocode" % "junit-interface" % "0.10" % "test",
    "org.apache.derby" % "derby" % "10.5.3.0_1" % "test",
    "javax.transaction" % "jta" % "1.1" % "provided"
  )

  
}
