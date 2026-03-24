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
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Macrobenchmark for measuring thread deletion performance.
 *
 * Inserts 20,000 messages into a conversation, then measures the time
 * to delete the entire thread using per-batch transactions.
 *
 * Two variants:
 * - [deleteThread20kMessages]: 1:1 conversation with attachments and reactions
 * - [deleteGroupThread20kMessages]: Group conversation with attachments, reactions,
 *   group receipts (5 members x 20k = 100k rows), and mentions
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
@RequiresApi(31)
class ThreadDeletionBenchmarks {
  @get:Rule
  val benchmarkRule = MacrobenchmarkRule()

  @Test
  fun deleteThread20kMessages() {
    benchmarkRule.measureRepeated(
      packageName = "org.thoughtcrime.securesms.benchmark",
      metrics = BenchmarkMetrics.threadDeletion,
      iterations = 1,
      compilationMode = CompilationMode.Partial(),
      setupBlock = {
        BenchmarkSetup.setup("thread-delete", device, timeout = 120_000L)
        killProcess()
        startActivityAndWait()
        device.waitForIdle()
        device.wait(Until.findObject(By.textContains("Buddy")), 10_000)
      }
    ) {
      BenchmarkSetup.deleteThread(device)
      device.wait(Until.gone(By.textContains("Buddy")), 300_000L)
    }
  }

  @Test
  fun deleteGroupThread20kMessages() {
    benchmarkRule.measureRepeated(
      packageName = "org.thoughtcrime.securesms.benchmark",
      metrics = BenchmarkMetrics.threadDeletion,
      iterations = 1,
      compilationMode = CompilationMode.Partial(),
      setupBlock = {
        BenchmarkSetup.setup("thread-delete-group", device, timeout = 180_000L)
        killProcess()
        startActivityAndWait()
        device.waitForIdle()
        device.wait(Until.findObject(By.textContains("Title")), 10_000)
      }
    ) {
      BenchmarkSetup.deleteThread(device)
      device.wait(Until.gone(By.textContains("Title")), 300_000L)
    }
  }

  @Ignore("Needs locally provided backup file not available in CI yet")
  @Test
  fun deleteGroupThread20kMessagesWithBackupRestore() {
    benchmarkRule.measureRepeated(
      packageName = "org.thoughtcrime.securesms.benchmark",
      metrics = BenchmarkMetrics.threadDeletion,
      iterations = 1,
      compilationMode = CompilationMode.Partial(),
      setupBlock = {
        BenchmarkSetup.setup("backup-restore", device, timeout = 60_000L)
        killProcess()

        startActivityAndWait()
        device.waitForIdle()
        device.wait(Until.findObject(By.textContains("CuQ75j")), 10_000)
      }
    ) {
      BenchmarkSetup.deleteThread(device)
      device.wait(Until.gone(By.textContains("CuQ75j")), 300_000L)
    }
  }
}
