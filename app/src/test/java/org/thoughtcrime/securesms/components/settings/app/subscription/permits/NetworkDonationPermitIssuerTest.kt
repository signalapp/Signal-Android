/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.permits

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import io.mockk.every
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.donations.permits.DonationPermitError
import org.signal.libsignal.net.RequestResult
import org.signal.network.rest.RestStatusCodeError
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class NetworkDonationPermitIssuerTest {

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  @Test
  fun `a successful response yields the serialized permit response bytes`() {
    val responseBytes = byteArrayOf(4, 5, 6)
    every { AppDependencies.donationsApi.createDonationPermits(any()) } returns RequestResult.Success(responseBytes)

    val result = NetworkDonationPermitIssuer.issue(byteArrayOf(1))

    assertThat(result.getOrNull()?.toList()).isEqualTo(responseBytes.toList())
  }

  @Test
  fun `a status-code error yields an IssuerUnavailable error`() {
    every { AppDependencies.donationsApi.createDonationPermits(any()) } returns RequestResult.NonSuccess(RestStatusCodeError(429, emptyMap(), null))

    val result = NetworkDonationPermitIssuer.issue(byteArrayOf(1))

    assertThat(result.leftOrNull()).isNotNull().isInstanceOf(DonationPermitError.IssuerUnavailable::class)
  }

  @Test
  fun `a network error yields an IssuerUnavailable error`() {
    every { AppDependencies.donationsApi.createDonationPermits(any()) } returns RequestResult.RetryableNetworkError(IOException("boom"))

    val result = NetworkDonationPermitIssuer.issue(byteArrayOf(1))

    assertThat(result.leftOrNull()).isNotNull().isInstanceOf(DonationPermitError.IssuerUnavailable::class)
  }
}
