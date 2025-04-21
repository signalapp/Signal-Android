/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import android.app.Application
import assertk.assertThat
import assertk.assertions.isTrue
import io.mockk.every
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.testutil.MockSignalStoreRule

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class InAppPaymentRedemptionJobTest {

  @get:Rule
  val mockSignalStore = MockSignalStoreRule()

  @Test
  fun `Given an unregistered local user, when I run, then I expect failure`() {
    every { mockSignalStore.account.isRegistered } returns false

    val job = InAppPaymentRedemptionJob.create()

    val result = job.run()

    assertThat(result.isFailure).isTrue()
  }
}
