import com.android.build.api.variant.AndroidComponentsExtension

/**
 * Adds the [org.signal.buildtools.PseudoLanguage] translations of a module's English strings to its debug builds, which
 * unit tests and previews use, so layouts can be stress tested against them without shipping them. Apply it after
 * the Android plugin.
 */

val moduleEnglishStrings = layout.projectDirectory.file("src/main/res/values/strings.xml")

extensions.configure<AndroidComponentsExtension<*, *, *>>("androidComponents") {
  onVariants(selector().withBuildType("debug")) { variant ->
    if (!moduleEnglishStrings.asFile.exists()) {
      return@onVariants
    }

    val task = tasks.register<GeneratePseudoLocalesTask>("generate${variant.name.replaceFirstChar { it.uppercase() }}PseudoLocales") {
      englishStrings.set(moduleEnglishStrings)
    }
    variant.sources.res?.addGeneratedSourceDirectory(task, GeneratePseudoLocalesTask::outputDirectory)
  }
}
