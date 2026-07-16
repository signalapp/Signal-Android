/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.megaphone

import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.deleteAll
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppDonations
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.testing.InAppPaymentsRule
import org.thoughtcrime.securesms.testing.SignalActivityRule
import org.thoughtcrime.securesms.util.VersionTracker

/**
 * The "user might be a sustainer" rule for the donations remote megaphone: the `standard_donate`
 * conditional (via [RemoteMegaphoneRepository.getRemoteMegaphoneToShow]) suppresses the megaphone for
 * existing donors and shows it otherwise.
 */
@RunWith(AndroidJUnit4::class)
class DonationMegaphoneGatingTest {

  @get:Rule
  val harness = SignalActivityRule()

  @get:Rule
  val iapRule = InAppPaymentsRule()

  @Before
  fun setUp() {
    SignalDatabase.inAppPayments.writableDatabase.deleteAll(InAppPaymentTable.TABLE_NAME)
    SignalDatabase.remoteMegaphones.debugRemoveAll()
    setSelfBadges(emptyList())

    // Freshly-installed test APKs report 0 days installed and no configured payment methods, both of
    // which independently fail shouldShowDonateMegaphone. Fix them so the donor badge is the only
    // variable across the gating tests.
    mockkStatic(VersionTracker::class)
    mockkObject(InAppDonations)
    every { VersionTracker.getDaysSinceFirstInstalled(any()) } returns 30L
    every { InAppDonations.hasAtLeastOnePaymentMethodAvailable() } returns true
  }

  @After
  fun tearDown() {
    unmockkStatic(VersionTracker::class)
    unmockkObject(InAppDonations)
  }

  @Test
  fun nonDonor_showsStandardDonateMegaphone() {
    val record = donateMegaphoneRecord(conditionalId = "standard_donate")
    SignalDatabase.remoteMegaphones.insert(record)

    assertThat(RemoteMegaphoneRepository.getRemoteMegaphoneToShow()?.uuid).isEqualTo(record.uuid)
    assertThat(RemoteMegaphoneRepository.hasRemoteMegaphoneToShow(canShowLocalDonate = true)).isTrue()
  }

  @Test
  fun sustainer_donorBadge_suppressesStandardDonateMegaphone() {
    SignalDatabase.remoteMegaphones.insert(donateMegaphoneRecord(conditionalId = "standard_donate"))
    setSelfBadges(listOf(donorBadge()))

    assertThat(RemoteMegaphoneRepository.getRemoteMegaphoneToShow()).isNull()
    assertThat(RemoteMegaphoneRepository.hasRemoteMegaphoneToShow(canShowLocalDonate = true)).isFalse()
  }
}
