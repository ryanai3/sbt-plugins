package org.allenai.plugins

import sbt._
import Keys._

/** Injects artifact and git information into a project.
  *
  * For this to work, you will need:
  * 1.  An organization defined that does not include hyphens.
  * 2.  An annotated tag in your current git repository.
  */
object VersionInjectorPlugin extends AutoPlugin {

  object autoImport {
    val injectVersion = TaskKey[Seq[File]](
      "injectVersion",
      "Generates the artifact.conf and git.conf version files"
    )
    val injectArtifact = TaskKey[File](
      "injectArtifact",
      "Generate the artifact.conf resource"
    )
    val injectGit = TaskKey[File](
      "injectGit",
      "Generate the git.conf resource"
    )
    val gitCommitDate = TaskKey[Long](
      "gitCommitDate",
      "The date in milliseconds of the current git commit"
    )
    val gitRemotes = TaskKey[Seq[String]](
      "gitRemotes",
      "A list of the remotes of this git repository"
    )
    val gitSha1 = TaskKey[String](
      "gitSha1",
      "The sha1 hash of the current git commit"
    )
    val gitDescribe = TaskKey[String](
      "gitDescribe",
      "The description of the current git commit"
    )
    val gitLocalSha1 = TaskKey[String](
      "gitLocalSha1",
      "Most recent commit in src directory of current project"
    )
  }

  import autoImport._

  override def requires: Plugins = plugins.JvmPlugin

  override def trigger: PluginTrigger = allRequirements

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    injectVersionTask,
    injectArtifactTask,
    injectGitTask,
    gitDescribeTask,
    gitLocalSha1Task,
    gitCommitDateTask,
    gitSha1Task,
    gitRemotesTask,
    resourceGenerators in Compile <+= injectVersion
  )

  private def executableName(command: String) = {
    val maybeOsName = sys.props.get("os.name").map(_.toLowerCase)
    val maybeIsWindows = maybeOsName.filter(_.contains("windows"))
    maybeIsWindows.map(_ => command + ".exe").getOrElse(command)
  }

  private def gitCommand(args: Any*) = Process(executableName("git") +: args.map(_.toString))

  // Git tasks
  val gitDescribeTask = gitDescribe := {
    (gitCommand("describe").!!).trim
  }

  val gitLocalSha1Task = gitLocalSha1 := {
    gitCommand("log", "-1", "--format=%H", baseDirectory.value).!!.trim // gets git commit of latest commit in basedir
  }

  val gitCommitDateTask =
    gitCommitDate := (gitCommand("log", "-1", "--format=%ct", "HEAD").!!).trim.toLong * 1000
  val gitSha1Task = gitSha1 := (gitCommand("rev-parse", "HEAD").!!).trim
  val gitRemotesTask = gitRemotes := {
    val remotes = gitCommand("remote").lines.toList
    for (remote <- remotes) yield {
      val url = (gitCommand("config", "--get", s"remote.${remote}.url").!!).trim
      url
    }
  }

  val injectVersionTask = injectVersion <<= (injectArtifact in Compile, injectGit in Compile) map {
    (artifactFile, gitFile) => Seq(artifactFile, gitFile)
  }

  val injectArtifactTask =
    injectArtifact <<= (resourceManaged in Compile, organization, name, version, streams) map {
      (resourceManaged, org, name, version, s) =>
        val artifactConfFile = resourceManaged / org / cleanArtifactName(name) / "artifact.conf"
        val artifactContents =
          "name: \"" + name + "\"\n" +
            "version: \"" + version + "\""

        s.log.info(s"Generating artifact.conf managed resource... (name|version: $name|$version)")

        IO.write(artifactConfFile, artifactContents)
        artifactConfFile
    }

  val injectGitTask =
    injectGit <<= (resourceManaged in Compile, organization, name, version, streams, gitRemotes, gitSha1, gitCommitDate) map { // scalastyle:ignore
      (resourceManaged, org, name, version, s, remotes, sha1, date) =>
        val gitConfFile = resourceManaged / org / cleanArtifactName(name) / "git.conf"

        s.log.info(s"Generating git.conf managed resource... (sha1: ${sha1})")

        def quote(s: String) = "\"" + s + "\""
        val gitContents =
          "sha1: " + quote(sha1) + "\n" +
            "remotes: " + (remotes map quote).mkString("[", ", ", "]") + "\n" +
            "date: " + quote(date.toString)
        IO.write(gitConfFile, gitContents)
        gitConfFile
    }

  // Gives us the git most recent commits for all the local projects that this project depends on
  // We have to use a dynamic task because generating tasks based on project dependencies
  // necessarily alters the task graph.

  private def cleanArtifactName(string: String) = string.replaceAll("-", "")

}
