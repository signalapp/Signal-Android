/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contacts.sync

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.signal.remoteconfig.RemoteConfig
import org.thoughtcrime.securesms.database.RecipientTable.RegisteredState
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testutil.RecipientTestRule

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class ContactDiscoveryTest {

  @get:Rule
  val recipients = RecipientTestRule()

  private val context: Application = ApplicationProvider.getApplicationContext()

  private val cds = FakeCdsServer()
  private val systemContacts = FakeSystemContacts()
  private lateinit var cdsState: FakeCdsStoreState

  @Before
  fun setUp() {
    shadowOf(context).grantPermissions(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)

    cdsState = FakeCdsStoreState(recipients.signalStore.misc).also { it.install() }
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
  fun `refreshAll does not create recipients for system contacts that are not registered`() {
    systemContacts.add(ALICE, "Alice")

    ContactDiscovery.refreshAll(context, notifyOfNewUsers = false)

    assertTrue(ALICE in cds.requests.single().newE164s)
    assertFalse(SignalDatabase.recipients.getByE164(ALICE).isPresent)
  }

  @Test
  fun `refreshAll creates a registered recipient with system info for a registered system contact`() {
    val account = cds.register(BOB)
    systemContacts.add(BOB, "Bob", "Builder")

    ContactDiscovery.refreshAll(context, notifyOfNewUsers = false)

    val record = SignalDatabase.recipients.getRecord(SignalDatabase.recipients.getByE164(BOB).get())
    assertEquals(RegisteredState.REGISTERED, record.registered)
    assertEquals(account.aci, record.aci)
    assertEquals(account.pni, record.pni)
    assertEquals(ProfileName.fromParts("Bob", "Builder"), record.systemProfileName)
    assertNotNull(record.systemContactUri)
  }

  @Test
  fun `refreshAll does not apply system info to a pre-existing recipient that cds does not return`() {
    val id = SignalDatabase.recipients.getOrInsertFromE164(ALICE)
    systemContacts.add(ALICE, "Alice")

    ContactDiscovery.refreshAll(context, notifyOfNewUsers = false)

    val record = SignalDatabase.recipients.getRecord(id)
    assertEquals(ProfileName.EMPTY, record.systemProfileName)
    assertNull(record.systemContactUri)
  }

  @Test
  fun `refreshAll clears system info for recipients removed from system contacts even when no remaining contacts are on signal`() {
    systemContacts.add(ALICE, "Alice")
    cds.register(BOB)
    systemContacts.add(BOB, "Bob")
    ContactDiscovery.refreshAll(context, notifyOfNewUsers = false)
    val id = SignalDatabase.recipients.getByE164(BOB).get()
    assertNotNull(SignalDatabase.recipients.getRecord(id).systemContactUri)

    systemContacts.remove(BOB)
    ContactDiscovery.refreshAll(context, notifyOfNewUsers = false)

    val record = SignalDatabase.recipients.getRecord(id)
    assertNull(record.systemContactUri)
    assertEquals(ProfileName.EMPTY, record.systemProfileName)
  }

  @Test
  fun `refreshAll creates a recipient once a previously unregistered system contact registers`() {
    systemContacts.add(ALICE, "Alice")
    ContactDiscovery.refreshAll(context, notifyOfNewUsers = false)
    assertFalse(SignalDatabase.recipients.getByE164(ALICE).isPresent)

    cds.register(ALICE)
    ContactDiscovery.refreshAll(context, notifyOfNewUsers = false)

    val secondRequest = cds.requests[1]
    assertTrue(ALICE in secondRequest.previousE164s)
    assertFalse(ALICE in secondRequest.newE164s)

    val record = SignalDatabase.recipients.getRecord(SignalDatabase.recipients.getByE164(ALICE).get())
    assertEquals(RegisteredState.REGISTERED, record.registered)
    assertEquals(ProfileName.asGiven("Alice"), record.systemProfileName)
  }

  @Test
  fun `refreshAll inserts a contact joined message when a system contact newly registers`() {
    every { recipients.signalStore.settings.isNotifyWhenContactJoinsSignal } returns true
    systemContacts.add(ALICE, "Alice")
    ContactDiscovery.refreshAll(context, notifyOfNewUsers = true)

    cds.register(ALICE)
    ContactDiscovery.refreshAll(context, notifyOfNewUsers = true)

    val id = SignalDatabase.recipients.getByE164(ALICE).get()
    val threadId = SignalDatabase.threads.getThreadIdFor(id)
    assertNotNull(threadId)
    assertEquals(1, SignalDatabase.messages.getMessageCountForThread(threadId!!))
  }

  @Test
  fun `refreshAll does not insert a contact joined message on the first directory fetch`() {
    every { recipients.signalStore.settings.isNotifyWhenContactJoinsSignal } returns true
    cds.register(ALICE)
    systemContacts.add(ALICE, "Alice")

    ContactDiscovery.refreshAll(context, notifyOfNewUsers = true)

    val id = SignalDatabase.recipients.getByE164(ALICE).get()
    assertNull(SignalDatabase.threads.getThreadIdFor(id))
    assertTrue(cdsState.hasSuccessfullyRetrievedDirectory)
  }

  @Test
  fun `syncRecipientInfoWithSystemContacts does not create recipients`() {
    systemContacts.add(ALICE, "Alice")

    ContactDiscovery.syncRecipientInfoWithSystemContacts(context)

    assertFalse(SignalDatabase.recipients.getByE164(ALICE).isPresent)
    assertTrue(cds.requests.isEmpty())
  }

  @Test
  fun `refresh for a single unknown recipient reports registration without touching the token`() {
    cds.register(BOB)
    val bob = SignalDatabase.recipients.getOrInsertFromE164(BOB)
    systemContacts.add(BOB, "Bob")

    val state = ContactDiscovery.refresh(context, Recipient.resolved(bob), notifyOfNewUsers = false)

    assertEquals(RegisteredState.REGISTERED, state)
    assertNull(cds.requests.single().token)
    assertNull(cdsState.cdsToken)
    assertTrue(SignalDatabase.cds.getAllE164s().isEmpty())
    assertEquals(ProfileName.asGiven("Bob"), SignalDatabase.recipients.getRecord(bob).systemProfileName)
  }

  companion object {
    private const val ALICE = "+15555550101"
    private const val BOB = "+15555550102"
  }
}
