package org.thoughtcrime.securesms.testing

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import org.thoughtcrime.securesms.SignalInstrumentationApplicationContext

/**
 * Custom runner that replaces application with [SignalInstrumentationApplicationContext].
 *
 * Before the application is created, it reads any [RemoteConfigForTest] declared on the
 * about-to-run test (passed by the Orchestrator as the `class` argument, `pkg.Class#method`) and
 * stages the values in [TestRemoteConfig] so the app can seed them into `RemoteConfig` at startup.
 */
@Suppress("unused")
class SignalTestRunner : AndroidJUnitRunner() {
  override fun onCreate(arguments: Bundle?) {
    TestRemoteConfig.pending = parseRemoteConfig(arguments?.getString("class"))
    super.onCreate(arguments)
  }

  override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application {
    return super.newApplication(cl, SignalInstrumentationApplicationContext::class.java.name, context)
  }

  /**
   * Resolves [RemoteConfigForTest] annotations from the targeted test(s). [classArg] is the
   * instrumentation `class` argument: a comma-separated list of `pkg.Class` or `pkg.Class#method`.
   * Method-level flags override class-level flags for the same key. Reflection failures (e.g. a
   * whole-suite run with no `class` arg) fall back to no overrides.
   */
  private fun parseRemoteConfig(classArg: String?): Map<String, Any> {
    if (classArg.isNullOrBlank()) {
      return emptyMap()
    }

    val flags = mutableMapOf<String, Any>()

    for (entry in classArg.split(",")) {
      val (className, methodName) = entry.trim().split("#", limit = 2).let { it[0] to it.getOrNull(1) }

      try {
        // initialize = false: only read annotations, don't run the test class's static init this early.
        val testClass = Class.forName(className, false, javaClass.classLoader)
        val method = methodName?.let { name -> testClass.declaredMethods.firstOrNull { it.name == name } }

        // Class annotation first, then method annotation so method-level flags override class-level ones.
        listOfNotNull(
          testClass.getAnnotation(RemoteConfigForTest::class.java),
          method?.getAnnotation(RemoteConfigForTest::class.java)
        ).forEach { annotation ->
          annotation.flags.forEach { flags[it.flag.key] = it.value }
          annotation.rawFlags.forEach { flags[it.key] = it.value }
        }
      } catch (_: ReflectiveOperationException) {
        // Class/method not resolvable in this run; leave overrides as-is.
      }
    }

    return flags
  }
}
