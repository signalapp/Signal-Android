/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.benchmark

import androidx.annotation.RequiresApi
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Macrobenchmark benchmarks for message processing performance.
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
    benchmarkRule.measureRepeated(
      packageName = "org.thoughtcrime.securesms.benchmark",
      metrics = BenchmarkMetrics.incomingMessageObserver + BenchmarkMetrics.messageContentProcessor,
      iterations = 5,
      compilationMode = CompilationMode.Partial(),
      setupBlock = {
        BenchmarkSetup.setup("message-send", device)

        killProcess()
        startActivityAndWait()
        device.waitForIdle()

        BenchmarkSetup.setupIndividualSend(device)

        val uiObject = device.wait(Until.findObject(By.textContains("Buddy")), 5_000)
        if (withConversationOpen) {
          uiObject.click()
        }
      }
    ) {

      BenchmarkSetup.releaseMessages(device)

      device.wait(Until.hasObject(By.textContains("101")), 10_000L)
    }
  }
}
