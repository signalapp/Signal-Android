/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.megaphone

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.every
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.thoughtcrime.securesms.testutil.MockSignalStoreRule
import kotlin.time.Duration.Companion.days

class TwoFactorReminderScheduleTest {

  @get:Rule
  val signalStore = MockSignalStoreRule()

  private val now = System.currentTimeMillis()
  private val schedule = TwoFactorReminderSchedule()

  @Before
  fun setUp() {
    every { signalStore.account.isPhoneNumberless } returns true
    every { signalStore.account.hasTwoFactorMethods } returns true
    every { signalStore.uiHints.hasVerifiedTwoFactorBefore() } returns false
    every { signalStore.uiHints.hasSnoozedTwoFactorReminder() } returns false
    every { signalStore.uiHints.twoFactorLastVerifiedTime } returns now - 15.days.inWholeMilliseconds
  }

  @Test
  fun `shows once the first interval has passed`() {
    assertThat(schedule.shouldDisplay(0, 0, 0, now)).isTrue()
  }

  @Test
  fun `hidden for accounts with a phone number`() {
    every { signalStore.account.isPhoneNumberless } returns false

    assertThat(schedule.shouldDisplay(0, 0, 0, now)).isFalse()
  }

  @Test
  fun `hidden when the account has no methods`() {
    every { signalStore.account.hasTwoFactorMethods } returns false

    assertThat(schedule.shouldDisplay(0, 0, 0, now)).isFalse()
  }

  @Test
  fun `hidden until two weeks after methods were first seen`() {
    every { signalStore.uiHints.twoFactorLastVerifiedTime } returns now - 13.days.inWholeMilliseconds

    assertThat(schedule.shouldDisplay(0, 0, 0, now)).isFalse()
  }

  @Test
  fun `waits six months after a verification`() {
    every { signalStore.uiHints.hasVerifiedTwoFactorBefore() } returns true
    every { signalStore.uiHints.twoFactorLastVerifiedTime } returns now - 100.days.inWholeMilliseconds

    assertThat(schedule.shouldDisplay(0, 0, 0, now)).isFalse()
  }

  @Test
  fun `shows six months after a verification`() {
    every { signalStore.uiHints.hasVerifiedTwoFactorBefore() } returns true
    every { signalStore.uiHints.twoFactorLastVerifiedTime } returns now - 184.days.inWholeMilliseconds

    assertThat(schedule.shouldDisplay(0, 0, 0, now)).isTrue()
  }

  @Test
  fun `hidden for a week after a snooze`() {
    every { signalStore.uiHints.hasSnoozedTwoFactorReminder() } returns true

    assertThat(schedule.shouldDisplay(1, now - 6.days.inWholeMilliseconds, 0, now)).isFalse()
  }

  @Test
  fun `shows again once a snooze has expired`() {
    every { signalStore.uiHints.hasSnoozedTwoFactorReminder() } returns true

    assertThat(schedule.shouldDisplay(1, now - 8.days.inWholeMilliseconds, 0, now)).isTrue()
  }
}
