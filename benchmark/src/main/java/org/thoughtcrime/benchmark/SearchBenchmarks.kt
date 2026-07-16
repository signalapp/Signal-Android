/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.benchmark

import android.Manifest
import android.os.Build
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
 * Macrobenchmark for searching from the conversation list.
 *
 * Seeds 50 conversations with 2,000 messages each (100,000 messages total), then performs the same
 * operations the app runs when a user searches from the conversation list: opening the search
 * toolbar, typing a query, and waiting for results. Measures the full-text search against the
 * search table via the [SearchRepository] trace sections.
 */
@RunWith(AndroidJUnit4::class)
@RequiresApi(31)
class SearchBenchmarks {
  @get:Rule
  val benchmarkRule = MacrobenchmarkRule()

  @OptIn(ExperimentalMetricApi::class)
  @Test
  fun conversationListSearch() {
    var setup = false
    benchmarkRule.measureRepeated(
      packageName = "org.thoughtcrime.securesms.benchmark",
      metrics = listOf(
        TraceSectionMetric("ConversationListSearch-Messages", Mode.Sum),
        TraceSectionMetric("ConversationListSearch-Threads", Mode.Sum)
      ),
      iterations = 3,
      compilationMode = CompilationMode.Partial(),
      setupBlock = {
        if (!setup) {
          BenchmarkSetup.setup("conversation-list-search", device, timeout = 600_000L)
          setup = true
        }
        killProcess()
        if (Build.VERSION.SDK_INT >= 33) {
          device.executeShellCommand("pm grant $packageName ${Manifest.permission.POST_NOTIFICATIONS}")
        }
        startActivityAndWait()
        device.waitForIdle()
      }
    ) {
      device.findObject(By.desc("Search")).click()

      val searchField = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 10_000L)
      searchField.text = SEARCH_QUERY

      device.wait(Until.hasObject(By.textContains("Buddy")), 10_000L)
    }
  }

  companion object {
    private const val SEARCH_QUERY = "lighthouse"
  }
}
