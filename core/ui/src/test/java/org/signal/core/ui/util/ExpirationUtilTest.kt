/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.util

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class ExpirationUtilTest {

  companion object {
    private val SECONDS_IN_WEEK = TimeUnit.DAYS.toSeconds(7).toInt()
    private val SECONDS_IN_DAY = TimeUnit.DAYS.toSeconds(1).toInt()
    private val SECONDS_IN_HOUR = TimeUnit.HOURS.toSeconds(1).toInt()
    private val SECONDS_IN_MINUTE = TimeUnit.MINUTES.toSeconds(1).toInt()
  }

  private val context: Context = ApplicationProvider.getApplicationContext()

  @Test
  fun `formats as seconds under a minute`() {
    assertDisplays(1, "1 second")
    for (seconds in 2 until 60) {
      assertDisplays(seconds, "$seconds seconds")
    }
  }

  @Test
  fun `formats as minutes for whole minutes under an hour`() {
    assertDisplays(SECONDS_IN_MINUTE, "1 minute")
    for (minutes in 2 until 60) {
      assertDisplays(minutes * SECONDS_IN_MINUTE, "$minutes minutes")
    }
  }

  @Test
  fun `formats as hours for whole hours under a day`() {
    assertDisplays(SECONDS_IN_HOUR, "1 hour")
    for (hours in 2 until 24) {
      assertDisplays(hours * SECONDS_IN_HOUR, "$hours hours")
    }
  }

  @Test
  fun `formats as days for whole days under a week`() {
    assertDisplays(SECONDS_IN_DAY, "1 day")
    for (days in 2 until 7) {
      assertDisplays(days * SECONDS_IN_DAY, "$days days")
    }
  }

  @Test
  fun `formats as weeks for whole weeks`() {
    assertDisplays(SECONDS_IN_WEEK, "1 week")
    for (weeks in 2 until 52) {
      assertDisplays(weeks * SECONDS_IN_WEEK, "$weeks weeks")
    }
  }

  @Test
  fun `breaks down durations longer than a week into weeks and days only`() {
    assertDisplays(SECONDS_IN_WEEK + SECONDS_IN_DAY, "1 week 1 day")
    assertDisplays(SECONDS_IN_WEEK + SECONDS_IN_HOUR, "1 week")
    assertDisplays(SECONDS_IN_WEEK + SECONDS_IN_MINUTE, "1 week")
    assertDisplays(SECONDS_IN_WEEK + 1, "1 week")
    assertDisplays(SECONDS_IN_WEEK + SECONDS_IN_DAY + SECONDS_IN_HOUR, "1 week 1 day")
    assertDisplays(SECONDS_IN_WEEK + SECONDS_IN_DAY + SECONDS_IN_HOUR + SECONDS_IN_MINUTE, "1 week 1 day")
    assertDisplays(SECONDS_IN_WEEK + SECONDS_IN_DAY + SECONDS_IN_HOUR + SECONDS_IN_MINUTE + 1, "1 week 1 day")
    assertDisplays(SECONDS_IN_WEEK + SECONDS_IN_HOUR + SECONDS_IN_MINUTE, "1 week")
    assertDisplays(SECONDS_IN_WEEK + SECONDS_IN_HOUR + SECONDS_IN_MINUTE + 1, "1 week")
    assertDisplays(SECONDS_IN_WEEK + SECONDS_IN_MINUTE + 1, "1 week")
  }

  @Test
  fun `breaks down durations longer than a day into days and hours only`() {
    assertDisplays(SECONDS_IN_DAY + SECONDS_IN_HOUR, "1 day 1 hour")
    assertDisplays(SECONDS_IN_DAY + SECONDS_IN_MINUTE, "1 day")
    assertDisplays(SECONDS_IN_DAY + 1, "1 day")
  }

  @Test
  fun `breaks down durations longer than an hour into hours and minutes only`() {
    assertDisplays(SECONDS_IN_HOUR + SECONDS_IN_MINUTE, "1 hour 1 minute")
    assertDisplays(SECONDS_IN_HOUR + 1, "1 hour")
  }

  @Test
  fun `breaks down durations longer than a minute into minutes and seconds`() {
    assertDisplays(SECONDS_IN_MINUTE + 1, "1 minute 1 second")
  }

  private fun assertDisplays(seconds: Int, expected: String) {
    assertThat(ExpirationUtil.getExpirationDisplayValue(context, seconds)).isEqualTo(expected)
  }
}
