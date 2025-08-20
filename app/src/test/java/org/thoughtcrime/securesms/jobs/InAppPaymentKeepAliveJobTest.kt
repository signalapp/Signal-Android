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
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.testutil.MockSignalStoreRule

class InAppPaymentKeepAliveJobTest {

  @get:Rule
  val mockSignalStore = MockSignalStoreRule()

  @Test
  fun `Given an unregistered local user, when I run, then I expect skip`() {
    every { mockSignalStore.account.isRegistered } returns false

    val job = InAppPaymentKeepAliveJob.create(InAppPaymentSubscriberRecord.Type.DONATION)

    val result = job.run()

    assertThat(result.isSuccess).isTrue()
  }
}
