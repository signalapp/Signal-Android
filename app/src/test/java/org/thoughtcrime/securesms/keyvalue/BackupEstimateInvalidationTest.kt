/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.keyvalue

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.testutil.SignalStoreRule
import kotlin.time.Duration.Companion.days

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class BackupEstimateInvalidationTest {

  @get:Rule
  val signalStore = SignalStoreRule()

  @Test
  fun `changing the message cutoff clears the remote estimate`() {
    SignalStore.backup.lastBackupUncompressedSize = 1234L

    SignalStore.backup.messageCuttoffDuration = 30.days

    assertNull(SignalStore.backup.lastBackupUncompressedSize)
  }

  @Test
  fun `setting the message cutoff to its current value leaves the estimate alone`() {
    SignalStore.backup.messageCuttoffDuration = 30.days
    SignalStore.backup.lastBackupUncompressedSize = 1234L

    SignalStore.backup.messageCuttoffDuration = 30.days

    assertEquals(1234L, SignalStore.backup.lastBackupUncompressedSize)
  }

  @Test
  fun `clearing the message cutoff also clears the remote estimate`() {
    SignalStore.backup.messageCuttoffDuration = 30.days
    SignalStore.backup.lastBackupUncompressedSize = 1234L

    SignalStore.backup.messageCuttoffDuration = null

    assertNull(SignalStore.backup.lastBackupUncompressedSize)
  }

  @Test
  fun `the local estimate survives a cutoff change, since local backups ignore the cutoff`() {
    SignalStore.backup.lastLocalBackupUncompressedSize = 5678L

    SignalStore.backup.messageCuttoffDuration = 30.days

    assertEquals(5678L, SignalStore.backup.lastLocalBackupUncompressedSize)
  }
}
