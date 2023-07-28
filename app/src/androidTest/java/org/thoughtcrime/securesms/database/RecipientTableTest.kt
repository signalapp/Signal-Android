package org.thoughtcrime.securesms.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.CursorUtil
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testing.SignalActivityRule
import org.thoughtcrime.securesms.util.FeatureFlags
import org.thoughtcrime.securesms.util.FeatureFlagsAccessor
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.api.push.ServiceId.PNI
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RecipientTableTest {

  @get:Rule
  val harness = SignalActivityRule()

  @Test
  fun givenAHiddenRecipient_whenIQueryAllContacts_thenIDoNotExpectHiddenToBeReturned() {
    val hiddenRecipient = harness.others[0]
    SignalDatabase.recipients.setProfileName(hiddenRecipient, ProfileName.fromParts("Hidden", "Person"))
    SignalDatabase.recipients.markHidden(hiddenRecipient)

    val results = SignalDatabase.recipients.queryAllContacts("Hidden")!!

    assertEquals(0, results.count)
  }

  @Test
  fun givenAHiddenRecipient_whenIGetSignalContacts_thenIDoNotExpectHiddenToBeReturned() {
    val hiddenRecipient = harness.others[0]
    SignalDatabase.recipients.setProfileName(hiddenRecipient, ProfileName.fromParts("Hidden", "Person"))
    SignalDatabase.recipients.markHidden(hiddenRecipient)

    val results: MutableList<RecipientId> = SignalDatabase.recipients.getSignalContacts(false)?.use {
      val ids = mutableListOf<RecipientId>()
      while (it.moveToNext()) {
        ids.add(RecipientId.from(CursorUtil.requireLong(it, RecipientTable.ID)))
      }

      ids
    }!!

    assertNotEquals(0, results.size)
    assertFalse(hiddenRecipient in results)
  }

  @Test
  fun givenAHiddenRecipient_whenIQuerySignalContacts_thenIDoNotExpectHiddenToBeReturned() {
    val hiddenRecipient = harness.others[0]
    SignalDatabase.recipients.setProfileName(hiddenRecipient, ProfileName.fromParts("Hidden", "Person"))
    SignalDatabase.recipients.markHidden(hiddenRecipient)

    val results = SignalDatabase.recipients.querySignalContacts("Hidden", false)!!

    assertEquals(0, results.count)
  }

  @Test
  fun givenAHiddenRecipient_whenIQueryNonGroupContacts_thenIDoNotExpectHiddenToBeReturned() {
    val hiddenRecipient = harness.others[0]
    SignalDatabase.recipients.setProfileName(hiddenRecipient, ProfileName.fromParts("Hidden", "Person"))
    SignalDatabase.recipients.markHidden(hiddenRecipient)

    val results = SignalDatabase.recipients.queryNonGroupContacts("Hidden", false)!!

    assertEquals(0, results.count)
  }

  @Test
  fun givenAHiddenRecipient_whenIGetNonGroupContacts_thenIDoNotExpectHiddenToBeReturned() {
    val hiddenRecipient = harness.others[0]
    SignalDatabase.recipients.setProfileName(hiddenRecipient, ProfileName.fromParts("Hidden", "Person"))
    SignalDatabase.recipients.markHidden(hiddenRecipient)

    val results: MutableList<RecipientId> = SignalDatabase.recipients.getNonGroupContacts(false)?.use {
      val ids = mutableListOf<RecipientId>()
      while (it.moveToNext()) {
        ids.add(RecipientId.from(CursorUtil.requireLong(it, RecipientTable.ID)))
      }

      ids
    }!!

    assertNotEquals(0, results.size)
    assertFalse(hiddenRecipient in results)
  }

  @Test
  fun givenABlockedRecipient_whenIQueryAllContacts_thenIDoNotExpectBlockedToBeReturned() {
    val blockedRecipient = harness.others[0]
    SignalDatabase.recipients.setProfileName(blockedRecipient, ProfileName.fromParts("Blocked", "Person"))
    SignalDatabase.recipients.setBlocked(blockedRecipient, true)

    val results = SignalDatabase.recipients.queryAllContacts("Blocked")!!

    assertEquals(0, results.count)
  }

  @Test
  fun givenABlockedRecipient_whenIGetSignalContacts_thenIDoNotExpectBlockedToBeReturned() {
    val blockedRecipient = harness.others[0]
    SignalDatabase.recipients.setProfileName(blockedRecipient, ProfileName.fromParts("Blocked", "Person"))
    SignalDatabase.recipients.setBlocked(blockedRecipient, true)

    val results: MutableList<RecipientId> = SignalDatabase.recipients.getSignalContacts(false)?.use {
      val ids = mutableListOf<RecipientId>()
      while (it.moveToNext()) {
        ids.add(RecipientId.from(CursorUtil.requireLong(it, RecipientTable.ID)))
      }

      ids
    }!!

    assertNotEquals(0, results.size)
    assertFalse(blockedRecipient in results)
  }

  @Test
  fun givenABlockedRecipient_whenIQuerySignalContacts_thenIDoNotExpectBlockedToBeReturned() {
    val blockedRecipient = harness.others[0]
    SignalDatabase.recipients.setProfileName(blockedRecipient, ProfileName.fromParts("Blocked", "Person"))
    SignalDatabase.recipients.setBlocked(blockedRecipient, true)

    val results = SignalDatabase.recipients.querySignalContacts("Blocked", false)!!

    assertEquals(0, results.count)
  }

  @Test
  fun givenABlockedRecipient_whenIQueryNonGroupContacts_thenIDoNotExpectBlockedToBeReturned() {
    val blockedRecipient = harness.others[0]
    SignalDatabase.recipients.setProfileName(blockedRecipient, ProfileName.fromParts("Blocked", "Person"))
    SignalDatabase.recipients.setBlocked(blockedRecipient, true)

    val results = SignalDatabase.recipients.queryNonGroupContacts("Blocked", false)!!

    assertEquals(0, results.count)
  }

  @Test
  fun givenABlockedRecipient_whenIGetNonGroupContacts_thenIDoNotExpectBlockedToBeReturned() {
    val blockedRecipient = harness.others[0]
    SignalDatabase.recipients.setProfileName(blockedRecipient, ProfileName.fromParts("Blocked", "Person"))
    SignalDatabase.recipients.setBlocked(blockedRecipient, true)

    val results: MutableList<RecipientId> = SignalDatabase.recipients.getNonGroupContacts(false)?.use {
      val ids = mutableListOf<RecipientId>()
      while (it.moveToNext()) {
        ids.add(RecipientId.from(CursorUtil.requireLong(it, RecipientTable.ID)))
      }

      ids
    }!!

    assertNotEquals(0, results.size)
    assertFalse(blockedRecipient in results)
  }

  @Test
  fun givenARecipientWithPniAndAci_whenIMarkItUnregistered_thenIExpectItToBeSplit() {
    FeatureFlagsAccessor.forceValue(FeatureFlags.PHONE_NUMBER_PRIVACY, true)

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
    FeatureFlagsAccessor.forceValue(FeatureFlags.PHONE_NUMBER_PRIVACY, true)

    val mainId = SignalDatabase.recipients.getAndPossiblyMerge(ACI_A, PNI_A, E164_A)
    val mainRecord = SignalDatabase.recipients.getRecord(mainId)

    SignalDatabase.recipients.splitForStorageSync(mainRecord.storageId!!)

    val byAci: RecipientId = SignalDatabase.recipients.getByAci(ACI_A).get()

    val byE164: RecipientId = SignalDatabase.recipients.getByE164(E164_A).get()
    val byPni: RecipientId = SignalDatabase.recipients.getByPni(PNI_A).get()

    assertEquals(mainId, byAci)
    assertEquals(byE164, byPni)
    assertNotEquals(byAci, byE164)
  }

  companion object {
    val ACI_A = ACI.from(UUID.fromString("aaaa0000-5a76-47fa-a98a-7e72c948a82e"))
    val PNI_A = PNI.from(UUID.fromString("aaaa1111-c960-4f6c-8385-671ad2ffb999"))
    const val E164_A = "+12222222222"
  }
}
