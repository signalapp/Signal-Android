/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.service

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import io.mockk.every
import io.mockk.just
import io.mockk.runs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.testutil.MockRandom
import org.thoughtcrime.securesms.testutil.MockSignalStoreRule
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class MessageBackupListenerTest {

  @get:Rule
  val rule = MockSignalStoreRule()

  @Test
  fun testGetNextScheduledExecutionTime() {
    val listener = MessageBackupListener()

    var nextTime = System.currentTimeMillis() + 1.days.inWholeMilliseconds
    every { SignalStore.backup.nextBackupTime } returns nextTime
    assertThat(listener.getNextScheduledExecutionTime(ApplicationProvider.getApplicationContext())).isEqualTo(nextTime)

    nextTime = System.currentTimeMillis() + 2.days.inWholeMilliseconds
    every { SignalStore.backup.nextBackupTime } returns nextTime
    assertThat(listener.getNextScheduledExecutionTime(ApplicationProvider.getApplicationContext())).isEqualTo(nextTime)

    nextTime = System.currentTimeMillis() + 8.hours.inWholeMilliseconds
    every { SignalStore.backup.nextBackupTime } returns nextTime
    assertThat(listener.getNextScheduledExecutionTime(ApplicationProvider.getApplicationContext())).isEqualTo(nextTime)

    nextTime = System.currentTimeMillis() + 7.days.inWholeMilliseconds
    every { SignalStore.backup.nextBackupTime } returns nextTime
    every { SignalStore.settings.backupHour } returns 2
    every { SignalStore.settings.backupMinute } returns 0
    every { SignalStore.backup.nextBackupTime = any() } just runs
    val adjustedTime = listener.getNextScheduledExecutionTime(ApplicationProvider.getApplicationContext())
    assertThat(adjustedTime).isGreaterThan(System.currentTimeMillis())
    assertThat(adjustedTime).isLessThan(System.currentTimeMillis() + 2.days.inWholeMilliseconds)
  }

  @Test
  fun testBackupJitterExactlyWithinJitterWindow() {
    val jitterWindowSeconds = Math.toIntExact(TimeUnit.MINUTES.toSeconds(10))
    val now = LocalDateTime.of(2024, 6, 7, 2, 55)
    val next = MessageBackupListener.getNextDailyBackupTimeFromNowWithJitter(now, 3, 0, jitterWindowSeconds)
    assertEquals(8, next.dayOfMonth)
  }

  @Test
  fun testBackupJitterWithinJitterWindow() {
    val jitterWindowSeconds = Math.toIntExact(TimeUnit.MINUTES.toSeconds(10))
    val now = LocalDateTime.of(2024, 6, 7, 2, 58)
    val next = MessageBackupListener.getNextDailyBackupTimeFromNowWithJitter(now, 3, 0, jitterWindowSeconds)
    assertEquals(8, next.dayOfMonth)
  }

  @Test
  fun testBackupJitterJustOutsideOfWindow() {
    val jitterWindowSeconds = Math.toIntExact(TimeUnit.MINUTES.toSeconds(10))
    val now = LocalDateTime.of(2024, 6, 7, 2, 54, 59)
    val next = MessageBackupListener.getNextDailyBackupTimeFromNowWithJitter(now, 3, 0, jitterWindowSeconds)
    assertEquals(7, next.dayOfMonth)
  }

  @Test
  fun testBackupJitter() {
    val jitterWindowSeconds = Math.toIntExact(TimeUnit.MINUTES.toSeconds(10))
    val now = LocalDateTime.of(2024, 6, 7, 3, 15, 0)
    val next = MessageBackupListener.getNextDailyBackupTimeFromNowWithJitter(now, 3, 0, jitterWindowSeconds)
    assertEquals(8, next.dayOfMonth)
  }

  @Test
  fun testBackupJitterWhenScheduledForMidnightButJitterMakesItRunJustBefore() {
    val mockRandom = MockRandom(listOf(1.minutes.inWholeSeconds.toInt()))
    val jitterWindowSeconds = 10.minutes.inWholeSeconds.toInt()
    val now: LocalDateTime = LocalDateTime.of(2024, 6, 27, 23, 57, 0)
    val next: LocalDateTime = MessageBackupListener.getNextDailyBackupTimeFromNowWithJitter(now, 0, 0, jitterWindowSeconds, mockRandom)

    assertTrue(Duration.between(now, next).toSeconds() > (1.days.inWholeSeconds - jitterWindowSeconds))
  }
}
