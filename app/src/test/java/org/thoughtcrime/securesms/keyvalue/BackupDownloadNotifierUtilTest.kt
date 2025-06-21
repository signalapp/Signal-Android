/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.keyvalue

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.Test
import org.thoughtcrime.securesms.keyvalue.protos.BackupDownloadNotifierState
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

class BackupDownloadNotifierUtilTest {
  @Test
  fun `Given within one day of expiration, when I setDownloadNotifierToTriggerAtHalfwayPoint, then I expect 0 interval`() {
    val expectedIntervalFromNow = 0.seconds
    val expiration = 30.days
    val now = 29.days

    val expected = BackupDownloadNotifierState(
      entitlementExpirationSeconds = expiration.inWholeSeconds,
      lastSheetDisplaySeconds = now.inWholeSeconds,
      intervalSeconds = expectedIntervalFromNow.inWholeSeconds,
      type = BackupDownloadNotifierState.Type.SHEET
    )

    val result = BackupDownloadNotifierUtil.setDownloadNotifierToTriggerAtHalfwayPoint(
      entitlementExpirationTime = expiration,
      now = now,
      state = null
    )

    assertThat(result).isEqualTo(expected)
  }

  @Test
  fun `Given within four days of expiration, when I setDownloadNotifierToTriggerAtHalfwayPoint, then I expect to be notified on the last day`() {
    val expectedIntervalFromNow = 3.days
    val expiration = 30.days
    val now = 26.days

    val expected = BackupDownloadNotifierState(
      entitlementExpirationSeconds = expiration.inWholeSeconds,
      lastSheetDisplaySeconds = now.inWholeSeconds,
      intervalSeconds = expectedIntervalFromNow.inWholeSeconds,
      type = BackupDownloadNotifierState.Type.SHEET
    )

    val result = BackupDownloadNotifierUtil.setDownloadNotifierToTriggerAtHalfwayPoint(
      entitlementExpirationTime = expiration,
      now = now,
      state = null
    )

    assertThat(result).isEqualTo(expected)
  }

  @Test
  fun `Given more than four days until expiration, when I setDownloadNotifierToTriggerAtHalfwayPoint, then I expect to be notified at the halfway point`() {
    val expectedIntervalFromNow = 5.days
    val expiration = 30.days
    val now = 20.days

    val expected = BackupDownloadNotifierState(
      entitlementExpirationSeconds = expiration.inWholeSeconds,
      lastSheetDisplaySeconds = now.inWholeSeconds,
      intervalSeconds = expectedIntervalFromNow.inWholeSeconds,
      type = BackupDownloadNotifierState.Type.SHEET
    )

    val result = BackupDownloadNotifierUtil.setDownloadNotifierToTriggerAtHalfwayPoint(
      entitlementExpirationTime = expiration,
      now = now,
      state = null
    )

    assertThat(result).isEqualTo(expected)
  }

  @Test
  fun `Given an expired entitlement, when I setDownloadNotifierToTriggerAtHalfwayPoint, then I expect null`() {
    val expiration = 28.days
    val now = 29.days

    val result = BackupDownloadNotifierUtil.setDownloadNotifierToTriggerAtHalfwayPoint(
      entitlementExpirationTime = expiration,
      now = now,
      state = null
    )

    assertThat(result).isNull()
  }

  @Test
  fun `Given a repeat expiration time, when I setDownloadNotifierToTriggerAtHalfwayPoint, then I expect to return the exact same state`() {
    val expiration = 30.days
    val now = 20.days

    val expectedState = BackupDownloadNotifierState(
      entitlementExpirationSeconds = expiration.inWholeSeconds,
      intervalSeconds = 0L,
      lastSheetDisplaySeconds = 0L,
      type = BackupDownloadNotifierState.Type.DIALOG
    )

    val result = BackupDownloadNotifierUtil.setDownloadNotifierToTriggerAtHalfwayPoint(
      entitlementExpirationTime = expiration,
      now = now,
      state = expectedState
    )

    assertThat(result).isEqualTo(expectedState)
  }

  @Test
  fun `Given a null state, when I snoozeDownloadNotifier, then I expect null`() {
    val result = BackupDownloadNotifierUtil.snoozeDownloadNotifier(state = null)

    assertThat(result).isNull()
  }

  @Test
  fun `Given a DIALOG type, when I snoozeDownloadNotifier, then I expect null`() {
    val state = BackupDownloadNotifierState(
      entitlementExpirationSeconds = 0L,
      intervalSeconds = 0L,
      lastSheetDisplaySeconds = 0L,
      type = BackupDownloadNotifierState.Type.DIALOG
    )

    val result = BackupDownloadNotifierUtil.snoozeDownloadNotifier(state = state)

    assertThat(result).isNull()
  }

  @Test
  fun `Given within one day of expiration, when I snoozeDownloadNotifier, then I expect dialog 4hrs before expiration`() {
    val now = 0.hours
    val expiration = 12.hours
    val state = BackupDownloadNotifierState(
      entitlementExpirationSeconds = expiration.inWholeSeconds,
      lastSheetDisplaySeconds = 0,
      intervalSeconds = 0
    )

    val expected = BackupDownloadNotifierState(
      entitlementExpirationSeconds = expiration.inWholeSeconds,
      lastSheetDisplaySeconds = now.inWholeSeconds,
      intervalSeconds = 8.hours.inWholeSeconds,
      type = BackupDownloadNotifierState.Type.DIALOG
    )

    val result = BackupDownloadNotifierUtil.snoozeDownloadNotifier(
      state = state,
      now = now
    )

    assertThat(result).isEqualTo(expected)
  }

  @Test
  fun `Given more than one day until expiration, when I snoozeDownloadNotifier, then I expect sheet one day before expiration`() {
    val now = 0.days
    val expiration = 5.days
    val state = BackupDownloadNotifierState(
      entitlementExpirationSeconds = expiration.inWholeSeconds,
      lastSheetDisplaySeconds = 0,
      intervalSeconds = 0
    )

    val expected = BackupDownloadNotifierState(
      entitlementExpirationSeconds = expiration.inWholeSeconds,
      lastSheetDisplaySeconds = now.inWholeSeconds,
      intervalSeconds = 4.days.inWholeSeconds,
      type = BackupDownloadNotifierState.Type.SHEET
    )

    val result = BackupDownloadNotifierUtil.snoozeDownloadNotifier(
      state = state,
      now = now
    )

    assertThat(result).isEqualTo(expected)
  }
}
