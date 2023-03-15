@file:OptIn(ExperimentalBaselineProfilesApi::class)

package org.thoughtcrime.benchmark

import android.content.ComponentName
import android.content.Intent
import androidx.benchmark.macro.ExperimentalBaselineProfilesApi
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

/**
 * WARNING! THIS WILL WIPE YOUR SIGNAL INSTALL
 *
 * Test that generates a Baseline profile from a user journey. Our journey is:
 *  - start the app
 *  - open a conversation
 */
class BaselineProfileGenerator {
  @get:Rule
  val baselineProfileRule = BaselineProfileRule()

  @Test
  fun startup() {
    var setup = false
    baselineProfileRule.collectBaselineProfile(
      packageName = "org.thoughtcrime.securesms",
      profileBlock = {
        if (!setup) {
          BenchmarkSetup.setup("cold-start", device)
          setup = true
        }
        startActivityAndWait()
        device.findObject(By.textContains("Buddy")).click();
        device.wait(Until.hasObject(By.textContains("Signal message")), 10_000L)
        device.wait(Until.hasObject(By.textContains("Test")), 5_000L)
      }
    )
  }
}
