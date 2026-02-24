/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.benchmark

import androidx.annotation.RequiresApi
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.TraceSectionMetric.Mode
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
class GroupMessageProcessingBenchmarks {
  @get:Rule
  val benchmarkRule = MacrobenchmarkRule()

  @Test
  fun groupMessageReceiveOnConversationList() {
    run(withConversationOpen = false)
  }

  @Test
  fun individualMessageReceiveOnConversation() {
    run(withConversationOpen = true)
  }

  private fun run(withConversationOpen: Boolean) {
    benchmarkRule.measureRepeated(
      packageName = "org.thoughtcrime.securesms.benchmark",
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
        ),
        TraceSectionMetric(
          sectionName = "IncomingMessageObserver#totalProcessing",
          mode = Mode.Sum
        )
      ),
      iterations = 5,
      compilationMode = CompilationMode.Partial(),
      setupBlock = {
        BenchmarkSetup.setup("group-message-send", device)

        killProcess()
        startActivityAndWait()
        device.waitForIdle()

        BenchmarkSetup.setupGroupSend(device)

        val uiObject = device.wait(Until.findObject(By.textContains("Title")), 5_000)
        if (withConversationOpen) {
          uiObject.click()
        }
      }
    ) {

      BenchmarkSetup.releaseMessages(device)

      device.wait(Until.hasObject(By.textContains("505")),10_000L)
    }
  }
}
