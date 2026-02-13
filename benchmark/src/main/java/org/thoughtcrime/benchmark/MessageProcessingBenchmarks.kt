/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.benchmark

import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.TraceSectionMetric.Mode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Macrobenchmark benchmarks for message processing performance.
 *
 * WARNING! THIS WILL WIPE YOUR SIGNAL INSTALL
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
@RequiresApi(31)
class MessageProcessingBenchmarks {
  @get:Rule
  val benchmarkRule = MacrobenchmarkRule()

  @Test
  fun individualMessageReceiveOnConversationList() {
    run(withConversationOpen = false)
  }

  @Test
  fun individualMessageReceiveOnConversation() {
    run(withConversationOpen = true)
  }

  private fun run(withConversationOpen: Boolean) {
    var setup = false
    benchmarkRule.measureRepeated(
      packageName = "org.thoughtcrime.securesms",
      metrics = listOf(
        TraceSectionMetric(
          sectionName = "IncomingMessageObserver#decryptMessage",
          mode = Mode.Average
        ),
        TraceSectionMetric(
          sectionName = "MessageContentProcessor#handleMessage",
          mode = Mode.Average
        ),
        TraceSectionMetric(
          sectionName = "IncomingMessageObserver#processMessage",
          mode = Mode.Average
        )
      ),
      iterations = 5,
      compilationMode = CompilationMode.Partial(),
      setupBlock = {
        if (!setup) {
          BenchmarkSetup.setup("message-send", device)
        }

        killProcess()
        startActivityAndWait()
        device.waitForIdle()

        BenchmarkSetup.setupIndividualSend(device)

        val uiObject = device.wait(Until.findObject(By.textContains("Bob")), 5_000)
        if (withConversationOpen) {
          uiObject.click()
        }
      }
    ) {

      BenchmarkSetup.releaseMessages(device)

      device.waitForAny(
        10_000L,
        By.textContains("101"),
        By.textContains("202"),
        By.textContains("303"),
        By.textContains("404"),
        By.textContains("505"),
      )
    }
  }
}

/**
 * Inspired by [androidx.test.uiautomator.WaitMixin]
 */
fun UiDevice.waitForAny(timeout: Long, vararg conditions: BySelector): Boolean {
  val startTime = SystemClock.uptimeMillis()

  var result = conditions.any { this.hasObject(it) }
  var elapsedTime = 0L
  while (!result && elapsedTime < timeout) {
    SystemClock.sleep(100)
    result = conditions.any { this.hasObject(it) }
    elapsedTime = SystemClock.uptimeMillis() - startTime
  }

  return result
}
