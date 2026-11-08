/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.subscriptions

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Test
import org.signal.network.util.JsonUtil

class ActiveSubscriptionTest {

  @Test
  fun `given an active subscription, when I check isPaymentFailure, then I expect false`() {
    val input = """{"subscription":{"level":2000,"billingCycleAnchor":1636124746.000000000,"endOfCurrentPeriod":1675609546.000000000,"active":true,"cancelAtPeriodEnd":false,"currency":"USD","amount":2000,"status":"active"},"chargeFailure":null}"""
    val activeSubscription = JsonUtil.fromJson(input, ActiveSubscription::class.java)

    assertThat(activeSubscription.isActive).isTrue()
    assertThat(activeSubscription.isFailedPayment).isFalse()
  }

  @Test
  fun `given an absent processor and payment method, when I decode, then I expect the fallback values`() {
    val input = """{"subscription":{"level":2000,"active":true,"status":"active"}}"""
    val subscription = JsonUtil.fromJson(input, ActiveSubscription::class.java).activeSubscription!!

    assertThat(subscription.processor).isEqualTo(ActiveSubscription.Processor.STRIPE)
    assertThat(subscription.paymentMethod).isEqualTo(ActiveSubscription.PaymentMethod.UNKNOWN)
  }

  @Test
  fun `given no active subscription, when I check isInProgress, then I expect false`() {
    assertThat(ActiveSubscription(null, null).isInProgress).isFalse()
  }
}
