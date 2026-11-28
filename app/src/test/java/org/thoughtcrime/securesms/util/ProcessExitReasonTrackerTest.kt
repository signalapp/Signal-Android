/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.util

import android.app.Application
import android.app.ApplicationExitInfo
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowActivityManager.ApplicationExitInfoBuilder

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [30])
class ProcessExitReasonTrackerTest {

  private val packageName = "org.thoughtcrime.securesms"

  @Test
  fun `filters out exits already seen`() {
    val old = exit(timestamp = 100, reason = ApplicationExitInfo.REASON_USER_REQUESTED)
    val new = exit(timestamp = 200, reason = ApplicationExitInfo.REASON_USER_REQUESTED)

    val result = ProcessExitReasonTracker.newMainProcessExits(listOf(new, old), packageName, lastSeenTimestamp = 100)

    assertThat(result.map { it.timestamp }).containsExactly(200L)
  }

  @Test
  fun `filters out other processes`() {
    val main = exit(timestamp = 100, reason = ApplicationExitInfo.REASON_OTHER)
    val other = exit(timestamp = 200, reason = ApplicationExitInfo.REASON_USER_REQUESTED, processName = "$packageName:other")

    val result = ProcessExitReasonTracker.newMainProcessExits(listOf(other, main), packageName, lastSeenTimestamp = 0)

    assertThat(result.map { it.timestamp }).containsExactly(100L)
  }

  @Test
  fun `sorts oldest first`() {
    val exits = listOf(exit(timestamp = 300), exit(timestamp = 100), exit(timestamp = 200))

    val result = ProcessExitReasonTracker.newMainProcessExits(exits, packageName, lastSeenTimestamp = 0)

    assertThat(result.map { it.timestamp }).isEqualTo(listOf(100L, 200L, 300L))
  }

  @Test
  fun `empty when nothing new`() {
    val result = ProcessExitReasonTracker.newMainProcessExits(listOf(exit(timestamp = 100)), packageName, lastSeenTimestamp = 100)

    assertThat(result).isEmpty()
  }

  @Test
  fun `describe names force stops`() {
    val description = ProcessExitReasonTracker.describe(exit(timestamp = 100, reason = ApplicationExitInfo.REASON_USER_REQUESTED))

    assertThat(description).contains("USER_REQUESTED (Force Stop)")
  }

  @Test
  fun `unrecognized reasons do not throw`() {
    assertThat(ProcessExitReasonTracker.reasonToString(9999)).isEqualTo("UNRECOGNIZED (9999)")
  }

  private fun exit(timestamp: Long, reason: Int = ApplicationExitInfo.REASON_OTHER, processName: String = packageName): ApplicationExitInfo {
    return ApplicationExitInfoBuilder.newBuilder()
      .setProcessName(processName)
      .setReason(reason)
      .setTimestamp(timestamp)
      .setDescription("test")
      .build()
  }
}
