/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.benchmark

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.TraceSectionMetric.Mode

@OptIn(ExperimentalMetricApi::class)
object BenchmarkMetrics {

  val incomingMessageObserver: List<TraceSectionMetric>
    get() = listOf(
      TraceSectionMetric("IncomingMessageObserver#decryptMessage", Mode.Average),
      TraceSectionMetric("IncomingMessageObserver#perMessageTransaction", Mode.Average),
      TraceSectionMetric("IncomingMessageObserver#processMessage", Mode.Average),
      TraceSectionMetric("IncomingMessageObserver#totalProcessing", Mode.Sum)
    )

  val dataMessageProcessor: List<TraceSectionMetric>
    get() = listOf(
      TraceSectionMetric("DataMessageProcessor#gv2PreProcessing", Mode.Average),
      TraceSectionMetric("DataMessageProcessor#messageInsert", Mode.Average),
      TraceSectionMetric("DataMessageProcessor#postProcess", Mode.Average)
    )

  val messageContentProcessor: List<TraceSectionMetric>
    get() = listOf(
      TraceSectionMetric("MessageContentProcessor#handleMessage", Mode.Average)
    )

  val deliveryReceipt: List<TraceSectionMetric>
    get() = listOf(
      TraceSectionMetric("ReceiptMessageProcessor#incrementDeliveryReceiptCounts", Mode.Average)
    )

  val readReceipt: List<TraceSectionMetric>
    get() = listOf(
      TraceSectionMetric("ReceiptMessageProcessor#incrementReadReceiptCounts", Mode.Average)
    )
}
