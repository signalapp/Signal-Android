/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.permits

import android.app.Application
import arrow.core.left
import arrow.core.right
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.Base64
import org.signal.donations.permits.DonationPermitError
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.zkgroup.ServerSecretParams
import org.signal.libsignal.zkgroup.donation.DonationPermit
import org.signal.libsignal.zkgroup.donation.DonationPermitDerivedKeyPair
import org.signal.libsignal.zkgroup.donation.DonationPermitRequestContext
import org.signal.libsignal.zkgroup.donation.DonationPermitResponse
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class DonationPermitsTest {

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  @Before
  fun setUp() {
    mockkObject(SignalStore)
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `the spent permit is base64 encoded`() {
    val permit = mintPermit()
    every { AppDependencies.donationPermitsRepository.spendOrAcquirePermit(any()) } returns permit.right()

    assertThat(DonationPermits.getDonationPermit()).isEqualTo(RequestResult.Success(Base64.encodeWithPadding(permit.serialize())))
  }

  @Test
  fun `when a permit cannot be obtained then a non-success result is returned`() {
    every { AppDependencies.donationPermitsRepository.spendOrAcquirePermit(any()) } returns DonationPermitError.IssuerUnavailable(statusCode = 429).left()

    assertThat(DonationPermits.getDonationPermit()).isInstanceOf(RequestResult.NonSuccess::class)
  }

  private fun mintPermit(): DonationPermit {
    val secret = ServerSecretParams.generate()
    val now = Instant.ofEpochSecond(1_700_000_000)
    val context = DonationPermitRequestContext.forCount(1)
    val keyPair = DonationPermitDerivedKeyPair.forExpiration(DonationPermitResponse.defaultExpiration(now), secret)
    val response = context.request().issue(keyPair)
    return context.receive(response, secret.publicParams, now).single()
  }
}
