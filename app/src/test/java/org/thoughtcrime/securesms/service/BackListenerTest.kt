/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.service

import org.junit.Assert
import org.junit.Test
import org.thoughtcrime.securesms.BaseUnitTest
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class BackListenerTest : BaseUnitTest() {

  @Test
  fun testBackupJitterExactlyWithinJitterWindow() {
    val jitterWindowSeconds = Math.toIntExact(TimeUnit.MINUTES.toSeconds(10))
    val now = LocalDateTime.of(2024, 6, 7, 2, 55)
    val next = MessageBackupListener.getNextDailyBackupTimeFromNowWithJitter(now, 3, 0, jitterWindowSeconds)
    Assert.assertEquals(8, next.dayOfMonth)
  }

  @Test
  fun testBackupJitterWithinJitterWindow() {
    val jitterWindowSeconds = Math.toIntExact(TimeUnit.MINUTES.toSeconds(10))
    val now = LocalDateTime.of(2024, 6, 7, 2, 58)
    val next = MessageBackupListener.getNextDailyBackupTimeFromNowWithJitter(now, 3, 0, jitterWindowSeconds)
    Assert.assertEquals(8, next.dayOfMonth)
  }

  @Test
  fun testBackupJitterJustOutsideOfWindow() {
    val jitterWindowSeconds = Math.toIntExact(TimeUnit.MINUTES.toSeconds(10))
    val now = LocalDateTime.of(2024, 6, 7, 2, 54, 59)
    val next = MessageBackupListener.getNextDailyBackupTimeFromNowWithJitter(now, 3, 0, jitterWindowSeconds)
    Assert.assertEquals(7, next.dayOfMonth)
  }

  @Test
  fun testBackupJitter() {
    val jitterWindowSeconds = Math.toIntExact(TimeUnit.MINUTES.toSeconds(10))
    val now = LocalDateTime.of(2024, 6, 7, 3, 15, 0)
    val next = MessageBackupListener.getNextDailyBackupTimeFromNowWithJitter(now, 3, 0, jitterWindowSeconds)
    Assert.assertEquals(8, next.dayOfMonth)
  }
}
