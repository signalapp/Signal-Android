/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.push.exceptions

import org.junit.Assert.assertEquals
import org.junit.Test
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription
import org.whispersystems.signalservice.internal.util.JsonUtil

class InAppPaymentProcessorErrorTest {

  companion object {
    private val TEST_PROCESSOR = ActiveSubscription.Processor.STRIPE
    private const val TEST_CODE = "account_closed"
    private const val TEST_MESSAGE = "test_message"
    private const val TEST_OUTCOME_NETWORK_STATUS = "test_outcomeNetworkStatus"
    private const val TEST_OUTCOME_REASON = "test_outcomeReason"
    private const val TEST_OUTCOME_TYPE = "test_outcomeType"
    private val TEST_JSON = """
      {
      "processor": "${TEST_PROCESSOR.code}",
      "chargeFailure": {
        "code": "$TEST_CODE",
        "message": "$TEST_MESSAGE",
        "outcomeNetworkStatus": "$TEST_OUTCOME_NETWORK_STATUS",
        "outcomeReason": "$TEST_OUTCOME_REASON",
        "outcomeType": "$TEST_OUTCOME_TYPE"
      }
    
    }
    """.trimIndent()
  }

  @Test
  fun givenTestJson_whenIFromJson_thenIExpectProperlyParsedError() {
    val result = JsonUtil.fromJson(TEST_JSON, InAppPaymentProcessorError::class.java)

    assertEquals(TEST_PROCESSOR, result.processor)
    assertEquals(TEST_CODE, result.chargeFailure.code)
    assertEquals(TEST_OUTCOME_TYPE, result.chargeFailure.outcomeType)
    assertEquals(TEST_OUTCOME_NETWORK_STATUS, result.chargeFailure.outcomeNetworkStatus)
  }
}
