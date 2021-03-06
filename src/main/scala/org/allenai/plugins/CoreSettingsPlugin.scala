package org.allenai.plugins

import sbt._
import sbt.Keys._

import scalariform.formatter.preferences.{ DoubleIndentClassDeclaration, FormattingPreferences }
import scalariform.sbt.ScalariformPlugin

object CoreSettingsPlugin extends AutoPlugin {

  // Automatically add the StylePlugin and VersionInjectorPlugin
  override def requires: Plugins = ScalariformPlugin && StylePlugin && VersionInjectorPlugin

  // Automatically enable the plugin (no need for projects to `enablePlugins(CoreSettingsPlugin)`)
  override def trigger: PluginTrigger = allRequirements

  object autoImport {
    val CoreResolvers = CoreRepositories.Resolvers
    val PublishTo = CoreRepositories.PublishTo

    val generateRunClass = taskKey[File](
      "creates the run-class.sh script in the managed resources directory"
    )
  }

  val generateRunClassTask = autoImport.generateRunClass := {
    val logger = streams.value.log
    logger.info("Generating run-class.sh")
    val file = (resourceManaged in Compile).value / "run-class.sh"
    // Read the plugin's resource file.
    val contents = {
      val is = this.getClass.getClassLoader.getResourceAsStream("run-class.sh")
      try {
        IO.readBytes(is)
      } finally {
        is.close()
      }
    }

    // Copy the contents to the clients managed resources.
    IO.write(file, contents)
    logger.info(s"Wrote ${contents.size} bytes to ${file.getPath}.")

    file
  }

  // Add the IntegrationTest config to the project. The `extend(Test)` part makes it so
  // classes in src/it have a classpath dependency on classes in src/test. This makes
  // it simple to share common test helper code.
  // See http://www.scala-sbt.org/release/docs/Testing.html#Custom+test+configuration
  override val projectConfigurations = Seq(Configurations.IntegrationTest extend (Test))

  // These settings will be automatically applied to projects
  override def projectSettings: Seq[Setting[_]] =
    Defaults.itSettings ++ Seq(
      generateRunClassTask,
      fork := true, // Forking for run, test is required sometimes, so fork always.
      scalaVersion := CoreDependencies.defaultScalaVersion,
      scalacOptions ++= Seq("-target:jvm-1.7", "-Xlint", "-deprecation", "-feature"),
      javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
      resolvers ++= CoreRepositories.Resolvers.defaults,
      dependencyOverrides ++= CoreDependencies.loggingDependencyOverrides,
      dependencyOverrides += "org.scala-lang" % "scala-library" % scalaVersion.value,
      // Override default scalariform settings.
      ScalariformPlugin.autoImport.scalariformPreferences := {
        FormattingPreferences().setPreference(DoubleIndentClassDeclaration, true)
      }
    )
}
