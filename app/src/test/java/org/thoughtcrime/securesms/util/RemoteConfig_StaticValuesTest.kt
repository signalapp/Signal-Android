package org.thoughtcrime.securesms.util

import org.junit.Before
import org.junit.Test
import kotlin.reflect.KProperty1
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberProperties

/**
 * Ensures we don't release with forced values which is intended for local development only.
 */
class RemoteConfig_StaticValuesTest {

  @Before
  fun setup() {
    RemoteConfig.initialized = true
  }

  /**
   * This test cycles the REMOTE_VALUES through a bunch of different inputs, then looks at all of the public getters and checks to see if they return different
   * values when the inputs change. If they don't, then it's likely that the getter is returning a static value, which was likely introduced during testing
   * and not something we actually want to commit.
   */
  @Test
  fun `Ensure there's no static values`() {
    // A list of inputs we'll cycle the remote config values to in order to see if it changes the outputs of the getters
    val remoteTestInputs = listOf(
      true,
      false,
      "true",
      "false",
      "cat",
      "dog",
      "1",
      "100",
      "12345678910111213141516",
      "*"
    )

    val configKeys = RemoteConfig.configsByKey.keys

    val ignoreList = setOf(
      "initialized",
      "REMOTE_VALUES",
      "configsByKey",
      "debugMemoryValues",
      "debugDiskValues",
      "debugPendingDiskValues",
      "CRASH_PROMPT_CONFIG",
      "PROMPT_BATTERY_SAVER",
      "PROMPT_FOR_NOTIFICATION_LOGS",
      "DEVICE_SPECIFIC_NOTIFICATION_CONFIG"
    )

    val publicVals: List<KProperty1<*, *>> = RemoteConfig::class.memberProperties
      .filter { it.visibility == KVisibility.PUBLIC }
      .filterNot { ignoreList.contains(it.name) }

    val publicValOutputs: MutableMap<String, MutableSet<Any?>> = mutableMapOf()

    for (input in remoteTestInputs) {
      for (key in configKeys) {
        RemoteConfig.REMOTE_VALUES[key] = input
      }

      for (publicVal in publicVals) {
        val output: Any? = publicVal.getter.call(RemoteConfig)
        val existingOutputs: MutableSet<Any?> = publicValOutputs.getOrDefault(publicVal.name, mutableSetOf())
        existingOutputs.add(output)
        publicValOutputs[publicVal.name] = existingOutputs
      }
    }

    for (entry in publicValOutputs) {
      val getter = entry.key
      val outputs = entry.value

      if (outputs.size == 0) {
        throw AssertionError("Getter $getter has no outputs! Something is wrong.")
      }

      if (outputs.size == 1) {
        throw AssertionError("Getter '$getter' had the same output every time (value = ${outputs.first()})! Did you accidentally set it to a constant? Or, if you think this is a mistake, add a value to the inputs of this test that would case the value to change.")
      }
    }
  }
}
