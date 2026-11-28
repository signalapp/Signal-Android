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

class SignalLoginReminderScheduleTest {

  @get:Rule
  val signalStore = MockSignalStoreRule()

  private val now = System.currentTimeMillis()
  private val schedule = SignalLoginReminderSchedule()

  @Before
  fun setUp() {
    every { signalStore.account.isPhoneNumberless } returns true
    every { signalStore.uiHints.hasVerifiedSignalLoginBefore() } returns false
    every { signalStore.uiHints.hasSnoozedSignalLoginReminder() } returns false
    every { signalStore.uiHints.signalLoginLastVerifiedTime } returns now - 15.days.inWholeMilliseconds
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
  fun `hidden when the clock was never started`() {
    every { signalStore.uiHints.signalLoginLastVerifiedTime } returns 0

    assertThat(schedule.shouldDisplay(0, 0, 0, now)).isFalse()
  }

  @Test
  fun `hidden until two weeks after logging in`() {
    every { signalStore.uiHints.signalLoginLastVerifiedTime } returns now - 13.days.inWholeMilliseconds

    assertThat(schedule.shouldDisplay(0, 0, 0, now)).isFalse()
  }

  @Test
  fun `waits six months after a verification`() {
    every { signalStore.uiHints.hasVerifiedSignalLoginBefore() } returns true
    every { signalStore.uiHints.signalLoginLastVerifiedTime } returns now - 100.days.inWholeMilliseconds

    assertThat(schedule.shouldDisplay(0, 0, 0, now)).isFalse()
  }

  @Test
  fun `shows six months after a verification`() {
    every { signalStore.uiHints.hasVerifiedSignalLoginBefore() } returns true
    every { signalStore.uiHints.signalLoginLastVerifiedTime } returns now - 184.days.inWholeMilliseconds

    assertThat(schedule.shouldDisplay(0, 0, 0, now)).isTrue()
  }

  @Test
  fun `hidden for a week after a snooze`() {
    every { signalStore.uiHints.hasSnoozedSignalLoginReminder() } returns true

    assertThat(schedule.shouldDisplay(1, now - 6.days.inWholeMilliseconds, 0, now)).isFalse()
  }

  @Test
  fun `shows again once a snooze has expired`() {
    every { signalStore.uiHints.hasSnoozedSignalLoginReminder() } returns true

    assertThat(schedule.shouldDisplay(1, now - 8.days.inWholeMilliseconds, 0, now)).isTrue()
  }
}
