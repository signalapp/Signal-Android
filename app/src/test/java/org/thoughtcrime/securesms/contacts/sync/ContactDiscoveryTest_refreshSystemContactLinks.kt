/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contacts.sync

import android.app.Application
import android.net.Uri
import android.provider.ContactsContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.contacts.SystemContactsRepository.LinkedContact
import org.signal.contacts.SystemContactsRepository.LinkedContactResult
import org.signal.contacts.SystemContactsRepository.PhoneDetails
import org.thoughtcrime.securesms.database.RecipientTable.SystemContactLinkState
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testutil.RecipientTestRule

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class ContactDiscoveryTest_refreshSystemContactLinks {

  @get:Rule
  val recipients = RecipientTestRule()

  @Test
  fun `a contact that still holds the number refreshes every field`() {
    val id = linkedRecipient()

    ContactDiscovery.refreshSystemContactLinks { found(numbers = listOf(PhoneDetails(E164, ContactsContract.CommonDataKinds.Phone.TYPE_WORK, null))) }

    val record = SignalDatabase.recipients.getRecord(id)
    assertEquals(SystemContactLinkState.LINKED, record.systemContactLinkState)
    assertEquals(ProfileName.fromParts("Alicia", "Anderson"), record.systemProfileName)
    assertEquals(NEW_PHOTO_URI, record.systemContactPhotoUri)
    assertEquals(ContactsContract.CommonDataKinds.Phone.TYPE_WORK, record.systemPhoneType)
    assertEquals(E164, record.systemPhoneE164)
    assertEquals(NEW_CONTACT_URI, record.systemContactUri)
    Recipient.live(id).refresh()
    assertTrue(Recipient.resolved(id).isSystemContactByPhoneNumber)
  }

  @Test
  fun `a contact that no longer holds the number keeps the link`() {
    val id = linkedRecipient()

    ContactDiscovery.refreshSystemContactLinks { found(numbers = emptyList()) }

    val record = SignalDatabase.recipients.getRecord(id)
    assertEquals(SystemContactLinkState.LINKED, record.systemContactLinkState)
    assertEquals(NEW_CONTACT_URI, record.systemContactUri)
    assertEquals(-1, record.systemPhoneType)
    assertNull(record.systemPhoneE164)

    Recipient.live(id).refresh()
    val recipient = Recipient.resolved(id)
    assertTrue(recipient.isSystemContact)
    assertFalse(recipient.isSystemContactByPhoneNumber)
  }

  @Test
  fun `an undiscoverable recipient keeps the link but not the number`() {
    val id = linkedRecipient()
    SignalDatabase.recipients.updatePhoneNumberDiscoverability(presentInCds = emptySet(), missingFromCds = setOf(id))

    ContactDiscovery.refreshSystemContactLinks { found(numbers = listOf(PhoneDetails(E164, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE, null))) }
    Recipient.live(id).refresh()

    val recipient = Recipient.resolved(id)
    assertTrue(recipient.isSystemContact)
    assertFalse(recipient.isSystemContactByPhoneNumber)
  }

  @Test
  fun `a missing contact needs a new link and keeps everything but its URIs`() {
    val id = linkedRecipient()

    ContactDiscovery.refreshSystemContactLinks { LinkedContactResult.Missing }

    val record = SignalDatabase.recipients.getRecord(id)
    assertEquals(SystemContactLinkState.NEEDED, record.systemContactLinkState)
    assertNull(record.systemContactUri)
    assertNull(record.systemContactPhotoUri)
    assertEquals(ProfileName.fromParts("Alice", "Anderson"), record.systemProfileName)
    assertEquals(ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE, record.systemPhoneType)

    Recipient.live(id).refresh()
    assertFalse(Recipient.resolved(id).isSystemContact)
  }

  @Test
  fun `discovery by number links a recipient that needs a link`() {
    val id = linkedRecipient()
    ContactDiscovery.refreshSystemContactLinks { LinkedContactResult.Missing }

    link(id, OTHER_CONTACT_URI, ProfileName.fromParts("Alice", "Anderson"))

    val record = SignalDatabase.recipients.getRecord(id)
    assertEquals(SystemContactLinkState.LINKED, record.systemContactLinkState)
    assertEquals(OTHER_CONTACT_URI, record.systemContactUri)
  }

  @Test
  fun `dismissing a recipient that needs a link keeps its name`() {
    val id = linkedRecipient()
    ContactDiscovery.refreshSystemContactLinks { LinkedContactResult.Missing }

    SignalDatabase.recipients.dismissSystemContactLinkNeeded(id)

    val record = SignalDatabase.recipients.getRecord(id)
    assertEquals(SystemContactLinkState.NONE, record.systemContactLinkState)
    assertEquals(ProfileName.fromParts("Alice", "Anderson"), record.systemProfileName)
  }

  @Test
  fun `dismissing does not touch a linked recipient`() {
    val id = linkedRecipient()

    SignalDatabase.recipients.dismissSystemContactLinkNeeded(id)

    assertEquals(SystemContactLinkState.LINKED, SignalDatabase.recipients.getRecord(id).systemContactLinkState)
  }

  @Test
  fun `an unavailable provider leaves the link alone`() {
    val id = linkedRecipient()

    ContactDiscovery.refreshSystemContactLinks { LinkedContactResult.Unavailable }

    val record = SignalDatabase.recipients.getRecord(id)
    assertEquals(SystemContactLinkState.LINKED, record.systemContactLinkState)
    assertEquals(OLD_CONTACT_URI, record.systemContactUri)
    assertEquals(ProfileName.fromParts("Alice", "Anderson"), record.systemProfileName)
  }

  @Test
  fun `the stored lookup key is what gets looked up`() {
    linkedRecipient()
    val lookedUp = mutableListOf<String>()

    ContactDiscovery.refreshSystemContactLinks { lookupKey ->
      lookedUp += lookupKey
      LinkedContactResult.Unavailable
    }

    assertEquals(listOf("0r1-ABC"), lookedUp)
  }

  @Test
  fun `discovery by number does not replace an existing link`() {
    val id = linkedRecipient()

    link(id, OTHER_CONTACT_URI, ProfileName.fromParts("Someone", "Else"))

    val record = SignalDatabase.recipients.getRecord(id)
    assertEquals(OLD_CONTACT_URI, record.systemContactUri)
    assertEquals(ProfileName.fromParts("Alice", "Anderson"), record.systemProfileName)
  }

  @Test
  fun `a sync that does not match a linked recipient keeps the link`() {
    val id = linkedRecipient()

    SignalDatabase.recipients.beginBulkSystemContactUpdate().finish()

    assertEquals(OLD_CONTACT_URI, SignalDatabase.recipients.getRecord(id).systemContactUri)
  }

  private fun linkedRecipient(): RecipientId {
    val id = SignalDatabase.recipients.getOrInsertFromE164(E164)
    link(id, OLD_CONTACT_URI, ProfileName.fromParts("Alice", "Anderson"))
    return id
  }

  private fun link(id: RecipientId, contactUri: String, name: ProfileName) {
    val handle = SignalDatabase.recipients.beginBulkSystemContactUpdate()
    try {
      handle.setSystemContactInfo(
        id = id,
        systemProfileName = name,
        systemDisplayName = name.toString(),
        photoUri = OLD_PHOTO_URI,
        systemPhoneLabel = null,
        systemPhoneType = ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
        systemPhoneE164 = E164,
        systemContactUri = contactUri
      )
    } finally {
      handle.finish()
    }
    Recipient.live(id).refresh()
  }

  private fun found(numbers: List<PhoneDetails>): LinkedContactResult {
    return LinkedContactResult.Found(
      LinkedContact(
        contactUri = Uri.parse(NEW_CONTACT_URI),
        displayName = "Alicia Anderson",
        givenName = "Alicia",
        familyName = "Anderson",
        photoUri = NEW_PHOTO_URI,
        numbers = numbers
      )
    )
  }

  companion object {
    private const val E164 = "+15555550101"
    private const val OLD_CONTACT_URI = "content://com.android.contacts/contacts/lookup/0r1-ABC/1"
    private const val NEW_CONTACT_URI = "content://com.android.contacts/contacts/lookup/0r1-ABC.0r7-ABC/7"
    private const val OTHER_CONTACT_URI = "content://com.android.contacts/contacts/lookup/0r2-DEF/2"
    private const val OLD_PHOTO_URI = "content://com.android.contacts/contacts/1/photo"
    private const val NEW_PHOTO_URI = "content://com.android.contacts/contacts/7/photo"
  }
}
