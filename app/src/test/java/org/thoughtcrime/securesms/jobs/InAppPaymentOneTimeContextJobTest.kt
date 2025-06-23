/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import assertk.assertThat
import assertk.assertions.isTrue
import io.mockk.every
import org.junit.Rule
import org.junit.Test
import org.signal.donations.InAppPaymentType
import org.signal.donations.PaymentSourceType
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsTestRule
import org.thoughtcrime.securesms.testutil.MockSignalStoreRule

class InAppPaymentOneTimeContextJobTest {

  @get:Rule
  val mockSignalStore = MockSignalStoreRule()

  @get:Rule
  val iapRule = InAppPaymentsTestRule()

  @Test
  fun `Given an unregistered local user, when I run, then I expect failure`() {
    every { mockSignalStore.account.isRegistered } returns false

    val job = InAppPaymentOneTimeContextJob.create(iapRule.createInAppPayment(InAppPaymentType.ONE_TIME_DONATION, PaymentSourceType.PayPal))

    val result = job.run()

    assertThat(result.isFailure).isTrue()
  }
}
