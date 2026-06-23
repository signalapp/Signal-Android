/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.ServiceId.ACI
import org.signal.core.models.ServiceId.PNI
import org.signal.core.util.CursorUtil
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class RecipientTableTest {

  @get:Rule
  val recipients = RecipientTestRule()

  private lateinit var target: RecipientId
  private lateinit var other: RecipientId

  @Before
  fun setUp() {
    target = recipients.createRecipient("Target Person")
    other = recipients.createRecipient("Other Person")
  }

  @Test
  fun givenAHiddenRecipient_whenIQueryAllContacts_thenIExpectHiddenToBeReturned() {
    SignalDatabase.recipients.setProfileName(target, ProfileName.fromParts("Hidden", "Person"))
    SignalDatabase.recipients.markHidden(target)

    val results = SignalDatabase.recipients.queryAllContacts("Hidden", RecipientTable.IncludeSelfMode.Exclude)!!

    assertEquals(1, results.count)
  }

  @Test
  fun givenAHiddenRecipient_whenIGetSignalContacts_thenIDoNotExpectHiddenToBeReturned() {
    SignalDatabase.recipients.setProfileName(target, ProfileName.fromParts("Hidden", "Person"))
    SignalDatabase.recipients.markHidden(target)

    val results: MutableList<RecipientId> = SignalDatabase.recipients.getSignalContacts(RecipientTable.IncludeSelfMode.Exclude).use {
      val ids = mutableListOf<RecipientId>()
      while (it.moveToNext()) {
        ids.add(RecipientId.from(CursorUtil.requireLong(it, RecipientTable.ID)))
      }

      ids
    }

    assertNotEquals(0, results.size)
    assertFalse(target in results)
  }

  @Test
  fun givenAHiddenRecipient_whenIQuerySignalContacts_thenIDoNotExpectHiddenToBeReturned() {
    SignalDatabase.recipients.setProfileName(target, ProfileName.fromParts("Hidden", "Person"))
    SignalDatabase.recipients.markHidden(target)

    val results = SignalDatabase.recipients.querySignalContacts(RecipientTable.ContactSearchQuery("Hidden", RecipientTable.IncludeSelfMode.Exclude))!!

    assertEquals(0, results.count)
  }

  @Test
  fun givenAHiddenRecipient_whenIGetNonGroupContacts_thenIDoNotExpectHiddenToBeReturned() {
    SignalDatabase.recipients.setProfileName(target, ProfileName.fromParts("Hidden", "Person"))
    SignalDatabase.recipients.markHidden(target)

    val results: MutableList<RecipientId> = SignalDatabase.recipients.getNonGroupContacts(RecipientTable.IncludeSelfMode.Exclude)?.use {
      val ids = mutableListOf<RecipientId>()
      while (it.moveToNext()) {
        ids.add(RecipientId.from(CursorUtil.requireLong(it, RecipientTable.ID)))
      }

      ids
    }!!

    assertNotEquals(0, results.size)
    assertFalse(target in results)
  }

  @Test
  fun givenABlockedRecipient_whenIQueryAllContacts_thenIDoNotExpectBlockedToBeReturned() {
    SignalDatabase.recipients.setProfileName(target, ProfileName.fromParts("Blocked", "Person"))
    SignalDatabase.recipients.setBlocked(target, true)

    val results = SignalDatabase.recipients.queryAllContacts("Blocked", RecipientTable.IncludeSelfMode.Exclude)!!

    assertEquals(0, results.count)
  }

  @Test
  fun givenABlockedRecipient_whenIGetSignalContacts_thenIDoNotExpectBlockedToBeReturned() {
    SignalDatabase.recipients.setProfileName(target, ProfileName.fromParts("Blocked", "Person"))
    SignalDatabase.recipients.setBlocked(target, true)

    val results: MutableList<RecipientId> = SignalDatabase.recipients.getSignalContacts(RecipientTable.IncludeSelfMode.Exclude).use {
      val ids = mutableListOf<RecipientId>()
      while (it.moveToNext()) {
        ids.add(RecipientId.from(CursorUtil.requireLong(it, RecipientTable.ID)))
      }

      ids
    }

    assertNotEquals(0, results.size)
    assertFalse(target in results)
  }

  @Test
  fun givenABlockedRecipient_whenIQuerySignalContacts_thenIDoNotExpectBlockedToBeReturned() {
    SignalDatabase.recipients.setProfileName(target, ProfileName.fromParts("Blocked", "Person"))
    SignalDatabase.recipients.setBlocked(target, true)

    val results = SignalDatabase.recipients.querySignalContacts(RecipientTable.ContactSearchQuery("Blocked", RecipientTable.IncludeSelfMode.Exclude))!!

    assertEquals(0, results.count)
  }

  @Test
  fun givenABlockedRecipient_whenIGetNonGroupContacts_thenIDoNotExpectBlockedToBeReturned() {
    SignalDatabase.recipients.setProfileName(target, ProfileName.fromParts("Blocked", "Person"))
    SignalDatabase.recipients.setBlocked(target, true)

    val results: MutableList<RecipientId> = SignalDatabase.recipients.getNonGroupContacts(RecipientTable.IncludeSelfMode.Exclude)?.use {
      val ids = mutableListOf<RecipientId>()
      while (it.moveToNext()) {
        ids.add(RecipientId.from(CursorUtil.requireLong(it, RecipientTable.ID)))
      }

      ids
    }!!

    assertNotEquals(0, results.size)
    assertFalse(target in results)
  }

  @Test
  fun givenARecipientWithPniAndAci_whenIMarkItUnregistered_thenIExpectItToBeSplit() {
    val mainId = SignalDatabase.recipients.getAndPossiblyMerge(ACI_A, PNI_A, E164_A)

    SignalDatabase.recipients.markUnregistered(mainId)

    val byAci: RecipientId = SignalDatabase.recipients.getByAci(ACI_A).get()
    val byE164: RecipientId = SignalDatabase.recipients.getByE164(E164_A).get()
    val byPni: RecipientId = SignalDatabase.recipients.getByPni(PNI_A).get()

    assertEquals(mainId, byAci)
    assertEquals(byE164, byPni)
    assertNotEquals(byAci, byE164)
  }

  @Test
  fun givenARecipientWithPniAndAci_whenISplitItForStorageSync_thenIExpectItToBeSplit() {
    val mainId = SignalDatabase.recipients.getAndPossiblyMerge(ACI_A, PNI_A, E164_A)
    val mainRecord = SignalDatabase.recipients.getRecord(mainId)

    SignalDatabase.recipients.splitForStorageSyncIfNecessary(mainRecord.aci!!)

    val byAci: RecipientId = SignalDatabase.recipients.getByAci(ACI_A).get()
    val byE164: RecipientId = SignalDatabase.recipients.getByE164(E164_A).get()
    val byPni: RecipientId = SignalDatabase.recipients.getByPni(PNI_A).get()

    assertEquals(mainId, byAci)
    assertEquals(byE164, byPni)
    assertNotEquals(byAci, byE164)
  }

  @Test
  fun givenAnAlreadySyncedRecipient_whenIMarkItUnregistered_thenItsStorageIdRotatesSoTheChangePublishes() {
    // GIVEN a registered contact that already has a storage service id
    val mainId = SignalDatabase.recipients.getAndPossiblyMerge(ACI_A, PNI_A, E164_A)
    SignalDatabase.recipients.markRegistered(mainId, ACI_A)

    val originalStorageId: ByteArray? = SignalDatabase.recipients.getRecord(mainId).storageId
    assertNotNull("Precondition: an already-synced contact should have a storage id", originalStorageId)

    // WHEN it is marked unregistered
    SignalDatabase.recipients.markUnregistered(mainId)

    // THEN its storage id must rotate
    val updatedStorageId: ByteArray? = SignalDatabase.recipients.getRecord(mainId).storageId
    assertNotNull("Storage id should still be set after unregistering an already-synced contact", updatedStorageId)
    assertFalse(
      "Storage id should rotate when an already-synced contact is unregistered, so the change publishes to storage service",
      originalStorageId!!.contentEquals(updatedStorageId!!)
    )
  }

  companion object {
    val ACI_A = ACI.from(UUID.fromString("aaaa0000-5a76-47fa-a98a-7e72c948a82e"))
    val PNI_A = PNI.from(UUID.fromString("aaaa1111-c960-4f6c-8385-671ad2ffb999"))
    const val E164_A = "+12222222222"
  }
}
