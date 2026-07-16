package org.thoughtcrime.securesms.testing

import org.json.JSONObject
import org.thoughtcrime.securesms.util.RemoteConfig
import kotlin.reflect.KProperty0
import kotlin.reflect.jvm.isAccessible

/**
 * Declares remote config values a test needs. The [SignalTestRunner] reads this off the
 * about-to-run test (class and/or method) and stages the values into [TestRemoteConfig], which
 * [org.thoughtcrime.securesms.SignalInstrumentationApplicationContext] seeds into the real
 * [RemoteConfig] before the startup `init()` runs.
 *
 * Method-level annotations override class-level ones for the same key. Values are strings, matching
 * how the service delivers config; `"true"`/`"false"` are decoded into real booleans on the way into
 * the store (same as [org.signal.network.api.RemoteConfigApi]), other values stay strings.
 *
 * Prefer the typed [Flag] (which resolves its key from the actual [RemoteConfig] property); use
 * [RawFlag] for keys that don't have a [TestRemoteConfigFlag] entry.
 *
 * ```
 * @RemoteConfigForTest(
 *   flags = [Flag(TestRemoteConfigFlag.INTERNAL_USER, "true")],
 *   rawFlags = [RawFlag("android.someOtherKey", "1")]
 * )
 * class MyTest { ... }
 * ```
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class RemoteConfigForTest(
  val flags: Array<Flag> = [],
  val rawFlags: Array<RawFlag> = []
)

/** A flag whose key is resolved from the referenced [RemoteConfig] property at runtime. */
@Retention(AnnotationRetention.RUNTIME)
annotation class Flag(val flag: TestRemoteConfigFlag, val value: String)

/** A flag identified by its raw remote config key, for keys without a [TestRemoteConfigFlag] entry. */
@Retention(AnnotationRetention.RUNTIME)
annotation class RawFlag(val key: String, val value: String)

/**
 * Typed handles for remote config flags referenced by tests.
 */
enum class TestRemoteConfigFlag(private val property: KProperty0<*>) {
  INTERNAL_USER(RemoteConfig::internalUser),
  DEFAULT_MAX_BACKOFF(RemoteConfig::defaultMaxBackoff),
  DISAPPEAR_MORE(RemoteConfig::disappearMore);

  val key: String
    get() {
      property.isAccessible = true
      val delegate = property.getDelegate() ?: error("RemoteConfig.${property.name} has no delegate; only `by remoteX(...)` configs can be referenced by ${TestRemoteConfigFlag::class.simpleName}.")
      check(delegate is RemoteConfig.Config<*>) {
        "RemoteConfig.${property.name} delegate is ${delegate::class.simpleName}, not RemoteConfig.Config; cannot resolve its remote config key."
      }
      return delegate.key
    }
}

/**
 * Process-static bridge between [SignalTestRunner] (which knows the running test) and
 * [org.thoughtcrime.securesms.SignalInstrumentationApplicationContext] (which seeds the config).
 * Safe because Orchestrator runs each test in a fresh process.
 */
object TestRemoteConfig {
  @Volatile
  var pending: Map<String, Any> = emptyMap()

  /**
   * The staged config as a JSON string ready to write into `SignalStore.remoteConfig`. Mirrors
   * [org.signal.network.api.RemoteConfigApi]'s decode so `"true"`/`"false"` land as real booleans
   * (like the server path) while other values stay strings.
   */
  val json: String
    get() {
      val decoded = pending.mapValues { (_, value) -> (value as? String)?.lowercase()?.toBooleanStrictOrNull() ?: value }
      return JSONObject(decoded).toString()
    }
}
