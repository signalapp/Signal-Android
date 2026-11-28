/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contacts

import android.Manifest
import android.app.Application
import android.content.SyncResult
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.signal.remoteconfig.RemoteConfig
import org.thoughtcrime.securesms.contacts.sync.FakeCdsServer
import org.thoughtcrime.securesms.contacts.sync.FakeCdsStoreState
import org.thoughtcrime.securesms.contacts.sync.FakeSystemContacts
import org.thoughtcrime.securesms.testutil.RecipientTestRule

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class ContactsSyncAdapterTest {

  @get:Rule
  val recipients = RecipientTestRule()

  private val context: Application = ApplicationProvider.getApplicationContext()

  private val cds = FakeCdsServer()
  private val systemContacts = FakeSystemContacts()

  @Before
  fun setUp() {
    shadowOf(context).grantPermissions(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)

    FakeCdsStoreState(recipients.signalStore.misc).install()
    cds.install()
    systemContacts.install()

    every { RemoteConfig.cdsHardLimit } returns 50_000
  }

  @After
  fun tearDown() {
    cds.uninstall()
    systemContacts.uninstall()
  }

  @Test
  fun `short codes in the address book do not trigger a directory refresh on every sync`() {
    systemContacts.add(ALICE, "Alice")
    systemContacts.add(SHORT_CODE, "Bank")

    performSync()
    assertEquals(1, cds.requests.size)

    performSync()
    assertEquals(1, cds.requests.size)
  }

  @Test
  fun `a newly added system contact triggers a directory refresh`() {
    systemContacts.add(ALICE, "Alice")
    performSync()

    systemContacts.add(BOB, "Bob")
    performSync()

    assertEquals(2, cds.requests.size)
    assertEquals(setOf(BOB), cds.requests[1].newE164s)
  }

  private fun performSync() {
    ContactsSyncAdapter(context, false).onPerformSync(null, Bundle(), "", null, SyncResult())
  }

  companion object {
    private const val ALICE = "+15555550101"
    private const val BOB = "+15555550102"
    private const val SHORT_CODE = "72000"
  }
}
