package org.thoughtcrime.securesms.database

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.libsignal.util.guava.Optional
import java.lang.IllegalArgumentException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RecipientDatabaseTest {

  private lateinit var recipientDatabase: RecipientDatabase

  @Before
  fun setup() {
    recipientDatabase = DatabaseFactory.getRecipientDatabase(context)
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
    assertEquals(ACI_A, recipient.requireUuid())
    assertFalse(recipient.hasE164())
  }

  /** If all you have is an ACI, you can just store that, regardless of trust level. */
  @Test
  fun getAndPossiblyMerge_aciAndE164MapToNoOne_aciOnly_lowTrust() {
    val recipientId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, null, false)

    val recipient = Recipient.resolved(recipientId)
    assertEquals(ACI_A, recipient.requireUuid())
    assertFalse(recipient.hasE164())
  }

  /** If all you have is an E164, you can just store that, regardless of trust level. */
  @Test
  fun getAndPossiblyMerge_aciAndE164MapToNoOne_e164Only_highTrust() {
    val recipientId: RecipientId = recipientDatabase.getAndPossiblyMerge(null, E164_A, true)

    val recipient = Recipient.resolved(recipientId)
    assertEquals(E164_A, recipient.requireE164())
    assertFalse(recipient.hasUuid())
  }

  /** If all you have is an E164, you can just store that, regardless of trust level. */
  @Test
  fun getAndPossiblyMerge_aciAndE164MapToNoOne_e164Only_lowTrust() {
    val recipientId: RecipientId = recipientDatabase.getAndPossiblyMerge(null, E164_A, false)

    val recipient = Recipient.resolved(recipientId)
    assertEquals(E164_A, recipient.requireE164())
    assertFalse(recipient.hasUuid())
  }

  /** With high trust, you can associate an ACI-e164 pair. */
  @Test
  fun getAndPossiblyMerge_aciAndE164MapToNoOne_aciAndE164_highTrust() {
    val recipientId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_A, true)

    val recipient = Recipient.resolved(recipientId)
    assertEquals(ACI_A, recipient.requireUuid())
    assertEquals(E164_A, recipient.requireE164())
  }

  /** With low trust, you cannot associate an ACI-e164 pair, and therefore can only store the ACI. */
  @Test
  fun getAndPossiblyMerge_aciAndE164MapToNoOne_aciAndE164_lowTrust() {
    val recipientId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_A, false)

    val recipient = Recipient.resolved(recipientId)
    assertEquals(ACI_A, recipient.requireUuid())
    assertFalse(recipient.hasE164())
  }

  // ==============================================================
  // If the ACI maps to an existing user, but the E164 doesn't
  // ==============================================================

  /** With high trust, you can associate an e164 with an existing ACI. */
  @Test
  fun getAndPossiblyMerge_aciMapsToExistingUserButE164DoesNot_aciAndE164_highTrust() {
    val existingId: RecipientId = recipientDatabase.getOrInsertFromUuid(ACI_A)

    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_A, true)
    assertEquals(existingId, retrievedId)

    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_A, retrievedRecipient.requireUuid())
    assertEquals(E164_A, retrievedRecipient.requireE164())
  }

  /** With low trust, you cannot associate an ACI-e164 pair, and therefore cannot store the e164. */
  @Test
  fun getAndPossiblyMerge_aciMapsToExistingUserButE164DoesNot_aciAndE164_lowTrust() {
    val existingId: RecipientId = recipientDatabase.getOrInsertFromUuid(ACI_A)

    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_A, false)
    assertEquals(existingId, retrievedId)

    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_A, retrievedRecipient.requireUuid())
    assertFalse(retrievedRecipient.hasE164())
  }

  /** Basically the ‘change number’ case. High trust lets you update the existing user. */
  @Test
  fun getAndPossiblyMerge_aciMapsToExistingUserButE164DoesNot_aciAndE164_2_highTrust() {
    val existingId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_A, true)

    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_B, true)
    assertEquals(existingId, retrievedId)

    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_A, retrievedRecipient.requireUuid())
    assertEquals(E164_B, retrievedRecipient.requireE164())
  }

  /** Low trust means you can’t update the underlying data, but you also don’t need to create any new rows. */
  @Test
  fun getAndPossiblyMerge_aciMapsToExistingUserButE164DoesNot_aciAndE164_2_lowTrust() {
    val existingId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_A, true)

    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_B, false)
    assertEquals(existingId, retrievedId)

    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_A, retrievedRecipient.requireUuid())
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
    assertEquals(ACI_A, retrievedRecipient.requireUuid())
    assertEquals(E164_A, retrievedRecipient.requireE164())
  }

  /** With low trust, you cannot associate an ACI-e164 pair, and therefore need to create a new person with just the ACI. */
  @Test
  fun getAndPossiblyMerge_e164MapsToExistingUserButAciDoesNot_aciAndE164_lowTrust() {
    val existingId: RecipientId = recipientDatabase.getOrInsertFromE164(E164_A)

    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_A, false)
    assertNotEquals(existingId, retrievedId)

    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_A, retrievedRecipient.requireUuid())
    assertFalse(retrievedRecipient.hasE164())

    val existingRecipient = Recipient.resolved(existingId)
    assertEquals(E164_A, existingRecipient.requireE164())
    assertFalse(existingRecipient.hasUuid())
  }

  /** We never change the ACI of an existing row. New ACI = new person, regardless of trust. But high trust lets us take the e164 from the current holder. */
  @Test
  fun getAndPossiblyMerge_e164MapsToExistingUserButAciDoesNot_aciAndE164_2_highTrust() {
    val existingId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_A, true)

    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_B, E164_A, true)
    assertNotEquals(existingId, retrievedId)

    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_B, retrievedRecipient.requireUuid())
    assertEquals(E164_A, retrievedRecipient.requireE164())

    val existingRecipient = Recipient.resolved(existingId)
    assertEquals(ACI_A, existingRecipient.requireUuid())
    assertFalse(existingRecipient.hasE164())
  }

  /** We never change the ACI of an existing row. New ACI = new person, regardless of trust. And low trust means we can’t take the e164. */
  @Test
  fun getAndPossiblyMerge_e164MapsToExistingUserButAciDoesNot_aciAndE164_2_lowTrust() {
    val existingId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_A, true)

    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_B, E164_A, false)
    assertNotEquals(existingId, retrievedId)

    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_B, retrievedRecipient.requireUuid())
    assertFalse(retrievedRecipient.hasE164())

    val existingRecipient = Recipient.resolved(existingId)
    assertEquals(ACI_A, existingRecipient.requireUuid())
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
    assertEquals(ACI_A, retrievedRecipient.requireUuid())
    assertEquals(E164_A, retrievedRecipient.requireE164())
  }

  /** High trust lets you merge two different users into one. You should prefer the ACI user. Not shown: merging threads, dropping e164 sessions, etc. */
  @Test
  fun getAndPossiblyMerge_bothAciAndE164MapToExistingUser_aciAndE164_merge_highTrust() {
    val existingAciId: RecipientId = recipientDatabase.getOrInsertFromUuid(ACI_A)
    val existingE164Id: RecipientId = recipientDatabase.getOrInsertFromE164(E164_A)

    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_A, true)
    assertEquals(existingAciId, retrievedId)

    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_A, retrievedRecipient.requireUuid())
    assertEquals(E164_A, retrievedRecipient.requireE164())

    val existingE164Recipient = Recipient.resolved(existingE164Id)
    assertEquals(retrievedId, existingE164Recipient.id)
  }

  /** Low trust means you can’t merge. If you’re retrieving a user from the table with this data, prefer the ACI one. */
  @Test
  fun getAndPossiblyMerge_bothAciAndE164MapToExistingUser_aciAndE164_lowTrust() {
    val existingAciId: RecipientId = recipientDatabase.getOrInsertFromUuid(ACI_A)
    val existingE164Id: RecipientId = recipientDatabase.getOrInsertFromE164(E164_A)

    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_A, false)
    assertEquals(existingAciId, retrievedId)

    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_A, retrievedRecipient.requireUuid())
    assertFalse(retrievedRecipient.hasE164())

    val existingE164Recipient = Recipient.resolved(existingE164Id)
    assertEquals(E164_A, existingE164Recipient.requireE164())
    assertFalse(existingE164Recipient.hasUuid())
  }

  /** Another high trust case. No new rules here, just a more complex scenario to show how different rules interact. */
  @Test
  fun getAndPossiblyMerge_bothAciAndE164MapToExistingUser_aciAndE164_complex_highTrust() {
    val existingId1: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_B, true)
    val existingId2: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_B, E164_A, true)

    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_A, true)
    assertEquals(existingId1, retrievedId)

    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_A, retrievedRecipient.requireUuid())
    assertEquals(E164_A, retrievedRecipient.requireE164())

    val existingRecipient2 = Recipient.resolved(existingId2)
    assertEquals(ACI_B, existingRecipient2.requireUuid())
    assertFalse(existingRecipient2.hasE164())
  }

  /** Another low trust case. No new rules here, just a more complex scenario to show how different rules interact. */
  @Test
  fun getAndPossiblyMerge_bothAciAndE164MapToExistingUser_aciAndE164_complex_lowTrust() {
    val existingId1: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_B, true)
    val existingId2: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_B, E164_A, true)

    val retrievedId: RecipientId = recipientDatabase.getAndPossiblyMerge(ACI_A, E164_A, false)
    assertEquals(existingId1, retrievedId)

    val retrievedRecipient = Recipient.resolved(retrievedId)
    assertEquals(ACI_A, retrievedRecipient.requireUuid())
    assertEquals(E164_B, retrievedRecipient.requireE164())

    val existingRecipient2 = Recipient.resolved(existingId2)
    assertEquals(ACI_B, existingRecipient2.requireUuid())
    assertEquals(E164_A, existingRecipient2.requireE164())
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
    val recipientId: RecipientId = recipientDatabase.getOrInsertFromUuid(ACI_A)

    // WHEN I retrieve one by UUID
    val possible: Optional<RecipientId> = recipientDatabase.getByUuid(ACI_A)

    // THEN I get it back, and it has the properties I expect
    assertTrue(possible.isPresent)
    assertEquals(recipientId, possible.get())

    val recipient = Recipient.resolved(recipientId)
    assertTrue(recipient.uuid.isPresent)
    assertEquals(ACI_A, recipient.uuid.get())
  }

  @Test(expected = IllegalArgumentException::class)
  fun getAndPossiblyMerge_noArgs_invalid() {
    recipientDatabase.getAndPossiblyMerge(null, null, true)
  }

  private val context: Application
    get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application

  private fun ensureDbEmpty() {
    DatabaseFactory.getInstance(context).rawDatabase.rawQuery("SELECT COUNT(*) FROM ${RecipientDatabase.TABLE_NAME}", null).use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals(0, cursor.getLong(0))
    }
  }

  companion object {
    val ACI_A = UUID.fromString("3436efbe-5a76-47fa-a98a-7e72c948a82e")
    val ACI_B = UUID.fromString("8de7f691-0b60-4a68-9cd9-ed2f8453f9ed")

    val E164_A = "+12221234567"
    val E164_B = "+13331234567"
  }
}
