package org.thoughtcrime.securesms.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.ThreadUtil
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobs.RecipientChangedNumberJob
import org.thoughtcrime.securesms.keyvalue.AccountValues
import org.thoughtcrime.securesms.keyvalue.KeyValueDataSet
import org.thoughtcrime.securesms.keyvalue.KeyValueStore
import org.thoughtcrime.securesms.keyvalue.MockKeyValuePersistentStorage
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.libsignal.util.guava.Optional
import org.whispersystems.signalservice.api.push.ACI
import org.whispersystems.signalservice.api.push.PNI
import java.lang.IllegalArgumentException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RecipientDatabaseTest {

  private lateinit var recipientDatabase: RecipientDatabase

  @Before
  fun setup() {
    recipientDatabase = SignalDatabase.recipients
    ensureDbEmpty()
  }

  // ==============================================================
  // If both the ACI and E164 map to no one
  // ==============================================================

  /** If all you have is an ACI, you can just store that, regardless of trust level. */
  @Test
  fun getAndPossiblyMerge_aciAndE164MapToNoOne_aciOnly_highTrust() {
    val recipientId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, null, true)

    val recipient = Recipient.resolved(recipientId)
    assertEquals(ACI_A, recipient.requireAci())
    assertFalse(recipient.hasE164())
  }

  /** If all you have is an ACI, you can just store that, regardless of trust level. */
  @Test
  fun getAndPossiblyMerge_aciAndE164MapToNoOne_aciOnly_lowTrust() {
    val recipientId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, null, false)

    val recipient = Recipient.resolved(recipientId)
    assertEquals(ACI_A, recipient.requireAci())
    assertFalse(recipient.hasE164())
  }

  /** If all you have is an E164, you can just store that, regardless of trust level. */
  @Test
  fun getAndPossiblyMerge_aciAndE164MapToNoOne_e164Only_highTrust() {
    val recipientId: RecipientId = recipientDatabase.getAndPossiblyMerge(null, E164_A, true)

    val recipient = Recipient.resolved(recipientId)
    assertEquals(E164_A, recipient.requireE164())
    assertFalse(recipient.hasAci())
  }

  /** If all you have is an E164, you can just store that, regardless of trust level. */
  @Test
  fun getAndPossiblyMerge_aciAndE164MapToNoOne_e164Only_lowTrust() {
    val recipientId: RecipientId = recipientDatabase.getAndPossiblyMerge(null, E164_A, false)

    val recipient = Recipient.resolved(recipientId)
    assertEquals(E164_A, recipient.requireE164())
    assertFalse(recipient.hasAci())
  }

  /** With high trust, you can associate an ACI-e164 pair. */
  @Test
  fun getAndPossiblyMerge_aciAndE164MapToNoOne_aciAndE164_highTrust() {
    val recipientId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_A, true)

    val recipient = Recipient.resolved(recipientId)
    assertEquals(ACI_A, recipient.requireAci())
    assertEquals(E164_A, recipient.requireE164())
  }

  /** With low trust, you cannot associate an ACI-e164 pair, and therefore can only store the ACI. */
  @Test
  fun getAndPossiblyMerge_aciAndE164MapToNoOne_aciAndE164_lowTrust() {
    val recipientId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_A, false)

    val recipient = Recipient.resolved(recipientId)
    assertEquals(ACI_A, recipient.requireAci())
    assertFalse(recipient.hasE164())
  }

  // ==============================================================
  // If the ACI maps to an existing user, but the E164 doesn't
  // ==============================================================

  /** With high trust, you can associate an e164 with an existing ACI. */
  @Test
  fun getAndPossiblyMerge_aciMapsToExistingUserButE164DoesNot_aciAndE164_highTrust() {
    val existingId: RecipientId = recipientDatabase.getOrInsertFromAci(ACI_A)

    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_A, true)
    assertEquals(existingId, retrievedId)

    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_A, retrievedRecipient.requireAci())
    assertEquals(E164_A, retrievedRecipient.requireE164())
  }

  /** With low trust, you cannot associate an ACI-e164 pair, and therefore cannot store the e164. */
  @Test
  fun getAndPossiblyMerge_aciMapsToExistingUserButE164DoesNot_aciAndE164_lowTrust() {
    val existingId: RecipientId = recipientDatabase.getOrInsertFromAci(ACI_A)

    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_A, false)
    assertEquals(existingId, retrievedId)

    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_A, retrievedRecipient.requireAci())
    assertFalse(retrievedRecipient.hasE164())
  }

  /** Basically the ‘change number’ case. High trust lets you update the existing user. */
  @Test
  fun getAndPossiblyMerge_aciMapsToExistingUserButE164DoesNot_aciAndE164_2_highTrust() {
    val existingId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_A, true)

    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_B, true)
    assertEquals(existingId, retrievedId)

    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_A, retrievedRecipient.requireAci())
    assertEquals(E164_B, retrievedRecipient.requireE164())
  }

  /** Low trust means you can’t update the underlying data, but you also don’t need to create any new rows. */
  @Test
  fun getAndPossiblyMerge_aciMapsToExistingUserButE164DoesNot_aciAndE164_2_lowTrust() {
    val existingId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_A, true)

    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_B, false)
    assertEquals(existingId, retrievedId)

    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_A, retrievedRecipient.requireAci())
    assertEquals(E164_A, retrievedRecipient.requireE164())
  }

  // ==============================================================
  // If the E164 maps to an existing user, but the ACI doesn't
  // ==============================================================

  /** With high trust, you can associate an e164 with an existing ACI. */
  @Test
  fun getAndPossiblyMerge_e164MapsToExistingUserButAciDoesNot_aciAndE164_highTrust() {
    val existingId: RecipientId = recipientDatabase.getOrInsertFromE164(E164_A)

    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_A, true)
    assertEquals(existingId, retrievedId)

    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_A, retrievedRecipient.requireAci())
    assertEquals(E164_A, retrievedRecipient.requireE164())
  }

  /** With low trust, you cannot associate an ACI-e164 pair, and therefore need to create a new person with just the ACI. */
  @Test
  fun getAndPossiblyMerge_e164MapsToExistingUserButAciDoesNot_aciAndE164_lowTrust() {
    val existingId: RecipientId = recipientDatabase.getOrInsertFromE164(E164_A)

    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_A, false)
    assertNotEquals(existingId, retrievedId)

    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_A, retrievedRecipient.requireAci())
    assertFalse(retrievedRecipient.hasE164())

    val existingRecipient = Recipient.resolved(existingId)
    assertEquals(E164_A, existingRecipient.requireE164())
    assertFalse(existingRecipient.hasAci())
  }

  /** We never change the ACI of an existing row. New ACI = new person, regardless of trust. But high trust lets us take the e164 from the current holder. */
  @Test
  fun getAndPossiblyMerge_e164MapsToExistingUserButAciDoesNot_aciAndE164_2_highTrust() {
    val existingId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_A, true)
    recipientDatabase.setPni(existingId, PNI_A)

    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_B, E164_A, true)
    recipientDatabase.setPni(retrievedId, PNI_A)
    assertNotEquals(existingId, retrievedId)

    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_B, retrievedRecipient.requireAci())
    assertEquals(E164_A, retrievedRecipient.requireE164())
    assertEquals(PNI_A, retrievedRecipient.pni.get())

    val existingRecipient = Recipient.resolved(existingId)
    assertEquals(ACI_A, existingRecipient.requireAci())
    assertFalse(existingRecipient.hasE164())
    assertNull(existingRecipient.pni.orNull())
  }

  /** We never change the ACI of an existing row. New ACI = new person, regardless of trust. And low trust means we can’t take the e164. */
  @Test
  fun getAndPossiblyMerge_e164MapsToExistingUserButAciDoesNot_aciAndE164_2_lowTrust() {
    val existingId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_A, true)

    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_B, E164_A, false)
    assertNotEquals(existingId, retrievedId)

    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_B, retrievedRecipient.requireAci())
    assertFalse(retrievedRecipient.hasE164())

    val existingRecipient = Recipient.resolved(existingId)
    assertEquals(ACI_A, existingRecipient.requireAci())
    assertEquals(E164_A, existingRecipient.requireE164())
  }

  /** We never want to remove the e164 of our own contact entry. So basically treat this as a low-trust case, and leave the e164 alone. */
  @Test
  fun getAndPossiblyMerge_e164MapsToExistingUserButAciDoesNot_e164BelongsToLocalUser_highTrust() {
    val dataSet = KeyValueDataSet().apply {
      putString(AccountValues.KEY_E164, E164_A)
      putString(AccountValues.KEY_ACI, ACI_A.toString())
    }
    SignalStore.inject(KeyValueStore(MockKeyValuePersistentStorage.withDataSet(dataSet)))

    val existingId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_A, true)

    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_B, E164_A, true)
    assertNotEquals(existingId, retrievedId)

    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_B, retrievedRecipient.requireAci())
    assertFalse(retrievedRecipient.hasE164())

    val existingRecipient = Recipient.resolved(existingId)
    assertEquals(ACI_A, existingRecipient.requireAci())
    assertEquals(E164_A, existingRecipient.requireE164())
  }

  // ==============================================================
  // If both the ACI and E164 map to an existing user
  // ==============================================================

  /** Regardless of trust, if your ACI and e164 match, you’re good. */
  @Test
  fun getAndPossiblyMerge_bothAciAndE164MapToExistingUser_aciAndE164_highTrust() {
    val existingId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_A, true)

    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_A, true)
    assertEquals(existingId, retrievedId)

    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_A, retrievedRecipient.requireAci())
    assertEquals(E164_A, retrievedRecipient.requireE164())
  }

  /** High trust lets you merge two different users into one. You should prefer the ACI user. Not shown: merging threads, dropping e164 sessions, etc. */
  @Test
  fun getAndPossiblyMerge_bothAciAndE164MapToExistingUser_aciAndE164_merge_highTrust() {
    val changeNumberListener = ChangeNumberListener()
    changeNumberListener.enqueue()

    val existingAciId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, null, true)
    val existingE164Id: RecipientId = recipientDatabase.getAndPossiblyMerge(null, E164_A, true)

    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_A, true)
    assertEquals(existingAciId, retrievedId)

    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_A, retrievedRecipient.requireAci())
    assertEquals(E164_A, retrievedRecipient.requireE164())

    val existingE164Recipient = Recipient.resolved(existingE164Id)
    assertEquals(retrievedId, existingE164Recipient.id)

    changeNumberListener.waitForJobManager()
    assertFalse(changeNumberListener.numberChangeWasEnqueued)
  }

  /** Same as [getAndPossiblyMerge_bothAciAndE164MapToExistingUser_aciAndE164_merge_highTrust], but with a number change. */
  @Test
  fun getAndPossiblyMerge_bothAciAndE164MapToExistingUser_aciAndE164_merge_highTrust_changedNumber() {
    val changeNumberListener = ChangeNumberListener()
    changeNumberListener.enqueue()

    val existingAciId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_B, true)
    val existingE164Id: RecipientId = recipientDatabase.getAndPossiblyMerge(null, E164_A, true)

    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_A, true)
    assertEquals(existingAciId, retrievedId)

    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_A, retrievedRecipient.requireAci())
    assertEquals(E164_A, retrievedRecipient.requireE164())

    val existingE164Recipient = Recipient.resolved(existingE164Id)
    assertEquals(retrievedId, existingE164Recipient.id)

    changeNumberListener.waitForJobManager()
    assert(changeNumberListener.numberChangeWasEnqueued)
  }

  /** Low trust means you can’t merge. If you’re retrieving a user from the table with this data, prefer the ACI one. */
  @Test
  fun getAndPossiblyMerge_bothAciAndE164MapToExistingUser_aciAndE164_lowTrust() {
    val existingAciId: RecipientId = recipientDatabase.getOrInsertFromAci(ACI_A)
    val existingE164Id: RecipientId = recipientDatabase.getOrInsertFromE164(E164_A)

    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_A, false)
    assertEquals(existingAciId, retrievedId)

    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_A, retrievedRecipient.requireAci())
    assertFalse(retrievedRecipient.hasE164())

    val existingE164Recipient = Recipient.resolved(existingE164Id)
    assertEquals(E164_A, existingE164Recipient.requireE164())
    assertFalse(existingE164Recipient.hasAci())
  }

  /** Another high trust case. No new rules here, just a more complex scenario to show how different rules interact. */
  @Test
  fun getAndPossiblyMerge_bothAciAndE164MapToExistingUser_aciAndE164_complex_highTrust() {
    val changeNumberListener = ChangeNumberListener()
    changeNumberListener.enqueue()

    val existingId1: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_B, true)
    val existingId2: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_B, E164_A, true)

    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_A, true)
    assertEquals(existingId1, retrievedId)

    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_A, retrievedRecipient.requireAci())
    assertEquals(E164_A, retrievedRecipient.requireE164())

    val existingRecipient2 = Recipient.resolved(existingId2)
    assertEquals(ACI_B, existingRecipient2.requireAci())
    assertFalse(existingRecipient2.hasE164())

    assert(changeNumberListener.numberChangeWasEnqueued)
  }

  /** Another low trust case. No new rules here, just a more complex scenario to show how different rules interact. */
  @Test
  fun getAndPossiblyMerge_bothAciAndE164MapToExistingUser_aciAndE164_complex_lowTrust() {
    val existingId1: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_B, true)
    val existingId2: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_B, E164_A, true)

    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_A, false)
    assertEquals(existingId1, retrievedId)

    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_A, retrievedRecipient.requireAci())
    assertEquals(E164_B, retrievedRecipient.requireE164())

    val existingRecipient2 = Recipient.resolved(existingId2)
    assertEquals(ACI_B, existingRecipient2.requireAci())
    assertEquals(E164_A, existingRecipient2.requireE164())
  }

  /**
   * Another high trust case that results in a merge. Nothing strictly new here, but this case is called out because it’s a merge but *also* an E164 change,
   * which clients may need to know for UX purposes.
   */
  @Test
  fun getAndPossiblyMerge_bothAciAndE164MapToExistingUser_aciAndE164_mergeAndPhoneNumberChange_highTrust() {
    val existingId1: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_B, true)
    val existingId2: RecipientId = recipientDatabase.getAndPossiblyMerge(null, E164_A, true)

    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_A, true)
    assertEquals(existingId1, retrievedId)

    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_A, retrievedRecipient.requireAci())
    assertEquals(E164_A, retrievedRecipient.requireE164())

    assertFalse(recipientDatabase.getByE164(E164_B).isPresent)

    val recipientWithId2 = Recipient.resolved(existingId2)
    assertEquals(retrievedId, recipientWithId2.id)
  }

  /** We never want to remove the e164 of our own contact entry. So basically treat this as a low-trust case, and leave the e164 alone. */
  @Test
  fun getAndPossiblyMerge_bothAciAndE164MapToExistingUser_e164BelongsToLocalUser() {
    val dataSet = KeyValueDataSet().apply {
      putString(AccountValues.KEY_E164, E164_A)
      putString(AccountValues.KEY_ACI, ACI_B.toString())
    }
    SignalStore.inject(KeyValueStore(MockKeyValuePersistentStorage.withDataSet(dataSet)))

    val existingId1: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_B, E164_A, true)
    val existingId2: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, null, true)

    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_A, true)
    assertEquals(existingId2, retrievedId)

    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_A, retrievedRecipient.requireAci())
    assertFalse(retrievedRecipient.hasE164())

    val recipientWithId1 = Recipient.resolved(existingId1)
    assertEquals(ACI_B, recipientWithId1.requireAci())
    assertEquals(E164_A, recipientWithId1.requireE164())
  }

  /** This is a case where normally we'd update the E164 of a user, but here the changeSelf flag is disabled, so we shouldn't. */
  @Test
  fun getAndPossiblyMerge_aciMapsToExistingUserButE164DoesNot_aciBelongsToLocalUser_highTrust_changeSelfFalse() {
    val dataSet = KeyValueDataSet().apply {
      putString(AccountValues.KEY_E164, E164_A)
      putString(AccountValues.KEY_ACI, ACI_A.toString())
    }
    SignalStore.inject(KeyValueStore(MockKeyValuePersistentStorage.withDataSet(dataSet)))

    val existingId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_A, true)

    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_B, highTrust = true, changeSelf = false)
    assertEquals(existingId, retrievedId)

    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_A, retrievedRecipient.requireAci())
    assertEquals(E164_A, retrievedRecipient.requireE164())
  }

  /** This is a case where we're changing our own number, and it's allowed because changeSelf = true. */
  @Test
  fun getAndPossiblyMerge_aciMapsToExistingUserButE164DoesNot_aciBelongsToLocalUser_highTrust_changeSelfTrue() {
    val dataSet = KeyValueDataSet().apply {
      putString(AccountValues.KEY_E164, E164_A)
      putString(AccountValues.KEY_ACI, ACI_A.toString())
    }
    SignalStore.inject(KeyValueStore(MockKeyValuePersistentStorage.withDataSet(dataSet)))

    val existingId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_A, true)

    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_B, highTrust = true, changeSelf = true)
    assertEquals(existingId, retrievedId)

    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_A, retrievedRecipient.requireAci())
    assertEquals(E164_B, retrievedRecipient.requireE164())
  }

  /** Verifying a case where a change number job is expected to be enqueued. */
  @Test
  fun getAndPossiblyMerge_aciMapsToExistingUserButE164DoesNot_highTrust_changedNumber() {
    val changeNumberListener = ChangeNumberListener()
    changeNumberListener.enqueue()

    val existingId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_A, true)

    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_B, true)
    assertEquals(existingId, retrievedId)

    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_A, retrievedRecipient.requireAci())
    assertEquals(E164_B, retrievedRecipient.requireE164())

    changeNumberListener.waitForJobManager()
    assert(changeNumberListener.numberChangeWasEnqueued)
  }

  // ==============================================================
  // Misc
  // ==============================================================

  @Test
  fun createByE164SanityCheck() {
    // GIVEN one recipient
    val recipientId: RecipientId = recipientDatabase.getOrInsertFromE164(E164_A)

    // WHEN I retrieve one by E164
    val possible: Optional<RecipientId> = recipientDatabase.getByE164(E164_A)

    // THEN I get it back, and it has the properties I expect
    assertTrue(possible.isPresent)
    assertEquals(recipientId, possible.get())

    val recipient = Recipient.resolved(recipientId)
    assertTrue(recipient.e164.isPresent)
    assertEquals(E164_A, recipient.e164.get())
  }

  @Test
  fun createByUuidSanityCheck() {
    // GIVEN one recipient
    val recipientId: RecipientId = recipientDatabase.getOrInsertFromAci(ACI_A)

    // WHEN I retrieve one by UUID
    val possible: Optional<RecipientId> = recipientDatabase.getByAci(ACI_A)

    // THEN I get it back, and it has the properties I expect
    assertTrue(possible.isPresent)
    assertEquals(recipientId, possible.get())

    val recipient = Recipient.resolved(recipientId)
    assertTrue(recipient.aci.isPresent)
    assertEquals(ACI_A, recipient.aci.get())
  }

  @Test(expected = IllegalArgumentException::class)
  fun getAndPossiblyMerge_noArgs_invalid() {
    recipientDatabase.getAndPossiblyMerge(null, null, true)
  }

  private fun ensureDbEmpty() {
    SignalDatabase.rawDatabase.rawQuery("SELECT COUNT(*) FROM ${RecipientDatabase.TABLE_NAME}", null).use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals(0, cursor.getLong(0))
    }
  }

  private class ChangeNumberListener {

    var numberChangeWasEnqueued = false
      private set

    fun waitForJobManager() {
      ApplicationDependencies.getJobManager().flush()
      ThreadUtil.sleep(500)
    }

    fun enqueue() {
      ApplicationDependencies.getJobManager().addListener(
        { job -> job.factoryKey == RecipientChangedNumberJob.KEY },
        { _, _ -> numberChangeWasEnqueued = true }
      )
    }
  }

  companion object {
    val ACI_A = ACI.from(UUID.fromString("3436efbe-5a76-47fa-a98a-7e72c948a82e"))
    val ACI_B = ACI.from(UUID.fromString("8de7f691-0b60-4a68-9cd9-ed2f8453f9ed"))

    val PNI_A = PNI.from(UUID.fromString("154b8d92-c960-4f6c-8385-671ad2ffb999"))
    val PNI_B = PNI.from(UUID.fromString("ba92b1fb-cd55-40bf-adda-c35a85375533"))

    const val E164_A = "+12221234567"
    const val E164_B = "+13331234567"
  }
}
