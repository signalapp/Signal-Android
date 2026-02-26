/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.benchmark

import androidx.annotation.RequiresApi
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
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
    runGroupMessageReceive(withConversationOpen = false)
  }

  @Test
  fun groupMessageReceiveOnConversation() {
    runGroupMessageReceive(withConversationOpen = true)
  }

  private fun runGroupMessageReceive(withConversationOpen: Boolean) {
    benchmarkRule.measureRepeated(
      packageName = "org.thoughtcrime.securesms.benchmark",
      metrics = BenchmarkMetrics.incomingMessageObserver + BenchmarkMetrics.messageContentProcessor + BenchmarkMetrics.dataMessageProcessor,
      iterations = 5,
      compilationMode = CompilationMode.Partial(),
      setupBlock = {
        setupGroup("group-message-send", BenchmarkSetup::setupGroupSend, withConversationOpen)
      }
    ) {

      BenchmarkSetup.releaseMessages(device)

      device.wait(Until.hasObject(By.textContains("505")),10_000L)
    }
  }

  @Test
  fun groupDeliveryReceiptOnConversationList() {
    runGroupDeliveryReceipt(withConversationOpen = false)
  }

  @Test
  fun groupDeliveryReceiptOnConversation() {
    runGroupDeliveryReceipt(withConversationOpen = true)
  }

  private fun runGroupDeliveryReceipt(withConversationOpen: Boolean) {
    benchmarkRule.measureRepeated(
      packageName = "org.thoughtcrime.securesms.benchmark",
      metrics = BenchmarkMetrics.incomingMessageObserver + BenchmarkMetrics.messageContentProcessor + BenchmarkMetrics.deliveryReceipt,
      iterations = 5,
      compilationMode = CompilationMode.Partial(),
      setupBlock = {
        setupGroup("group-delivery-receipt", BenchmarkSetup::setupGroupDeliveryReceipt, withConversationOpen)
      }
    ) {
      BenchmarkSetup.releaseMessages(device)

      Thread.sleep(10_000)
    }
  }

  @Test
  fun groupReadReceiptOnConversationList() {
    runGroupReadReceipt(withConversationOpen = false)
  }

  @Test
  fun groupReadReceiptOnConversation() {
    runGroupReadReceipt(withConversationOpen = true)
  }

  private fun runGroupReadReceipt(withConversationOpen: Boolean) {
    benchmarkRule.measureRepeated(
      packageName = "org.thoughtcrime.securesms.benchmark",
      metrics = BenchmarkMetrics.incomingMessageObserver + BenchmarkMetrics.messageContentProcessor + BenchmarkMetrics.readReceipt,
      iterations = 5,
      compilationMode = CompilationMode.Partial(),
      setupBlock = {
        setupGroup("group-read-receipt", BenchmarkSetup::setupGroupReadReceipt, withConversationOpen)
      }
    ) {
      BenchmarkSetup.releaseMessages(device)

      Thread.sleep(10_000)
    }
  }

  private fun MacrobenchmarkScope.setupGroup(
    setupType: String,
    prepareCommand: (UiDevice) -> Unit,
    withConversationOpen: Boolean
  ) {
    BenchmarkSetup.setup(setupType, device)

    killProcess()
    startActivityAndWait()
    device.waitForIdle()

    prepareCommand(device)

    device.wait(Until.findObject(By.textContains("Title")), 5_000)
    if (withConversationOpen) {
      device.waitForIdle()
      device.findObject(By.textContains("Title")).click()
    }
  }
}
