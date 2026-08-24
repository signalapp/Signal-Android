/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import android.app.Application
import io.mockk.every
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.testutil.RecipientTestRule

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class RotateProfileKeyJobTest {

  @get:Rule
  val recipients = RecipientTestRule()

  private var unpublishedRotation: ByteArray? = null

  @Before
  fun setUp() {
    every { recipients.signalStore.account.notSyncedRotatedSelfProfileKey } answers { unpublishedRotation }
    every { recipients.signalStore.account.notSyncedRotatedSelfProfileKey = any() } answers { unpublishedRotation = firstArg() }
  }

  @Test
  fun `given I am the primary, when I run, then I rotate my profile key and record it as unpublished`() {
    val before = selfProfileKey()

    RotateProfileKeyJob().run()

    assertFalse(selfProfileKey().contentEquals(before))
    assertArrayEquals(selfProfileKey(), unpublishedRotation)
  }

  @Test
  fun `given I am a linked device, when I run, then I leave my profile key alone`() {
    every { recipients.signalStore.account.isLinkedDevice } returns true
    every { recipients.signalStore.account.isPrimaryDevice } returns false

    val before = selfProfileKey()

    RotateProfileKeyJob().run()

    assertArrayEquals(before, selfProfileKey())
    assertNull(unpublishedRotation)
  }

  private fun selfProfileKey(): ByteArray {
    return SignalDatabase.recipients.getRecord(recipients.self).profileKey.also { assertNotNull(it) }!!
  }
}
