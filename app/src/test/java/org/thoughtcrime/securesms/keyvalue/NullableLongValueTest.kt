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

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class NullableLongValueTest {

  @get:Rule
  val signalStore = SignalStoreRule()

  @Test
  fun `unset reads back as null`() {
    assertNull(SignalStore.backup.lastBackupUncompressedSize)
  }

  @Test
  fun `every long round trips, including the values a reserved sentinel would eat`() {
    for (value in listOf(-1L, 0L, 1L, -2L, Long.MIN_VALUE, Long.MAX_VALUE)) {
      SignalStore.backup.lastBackupUncompressedSize = value
      assertEquals("$value did not survive a round trip", value, SignalStore.backup.lastBackupUncompressedSize)
    }
  }

  @Test
  fun `assigning null after a value clears it back to null`() {
    SignalStore.backup.lastBackupUncompressedSize = 1234L
    assertEquals(1234L, SignalStore.backup.lastBackupUncompressedSize)

    SignalStore.backup.lastBackupUncompressedSize = null
    assertNull(SignalStore.backup.lastBackupUncompressedSize)
  }
}
