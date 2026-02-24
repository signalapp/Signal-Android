package org.signal.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * WARNING! THIS WILL WIPE YOUR SIGNAL INSTALL
 *
 * Test that generates a Baseline profile from a user journey. Our journey is:
 *  - start the app
 *  - open a conversation
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

  @get:Rule
  val baselineProfileRule = BaselineProfileRule()

  @Test
  fun startup() {
    var setup = false
    baselineProfileRule.collect(
      packageName = "org.thoughtcrime.securesms",
      includeInStartupProfile = true,
      profileBlock = {
        if (!setup) {
          BenchmarkSetup.setup("cold-start", device)
          setup = true
        }

        pressHome()
        startActivityAndWait()
        device.findObject(By.textContains("Buddy")).click()
        device.wait(Until.hasObject(By.textContains("Signal message")), 10_000L)
        device.wait(Until.hasObject(By.textContains("Test")), 5_000L)
      }
    )
  }
}
