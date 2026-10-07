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
import org.signal.core.models.ServiceId.ACI
import org.thoughtcrime.securesms.database.RecipientTable.SystemContactLinkState
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import java.util.UUID

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class ContactDiscoveryTest_linkSystemContact {

  @get:Rule
  val recipients = RecipientTestRule()

  @Test
  fun `a recipient with no number can be linked`() {
    val id = SignalDatabase.recipients.getOrInsertFromServiceId(ACI.from(UUID.randomUUID()))

    ContactDiscovery.linkSystemContact(id, contact(numbers = emptyList()))

    val record = SignalDatabase.recipients.getRecord(id)
    assertEquals(SystemContactLinkState.LINKED, record.systemContactLinkState)
    assertEquals(CONTACT_URI, record.systemContactUri)
    assertEquals(PHOTO_URI, record.systemContactPhotoUri)
    assertEquals(ProfileName.fromParts("Alice", "Anderson"), record.systemProfileName)
    assertNull(record.systemPhoneE164)

    Recipient.live(id).refresh()
    assertTrue(Recipient.resolved(id).isSystemContact)
  }

  @Test
  fun `an undiscoverable recipient can be linked, but its number is not vouched for`() {
    val id = SignalDatabase.recipients.getOrInsertFromE164(E164)
    SignalDatabase.recipients.updatePhoneNumberDiscoverability(presentInCds = emptySet(), missingFromCds = setOf(id))

    ContactDiscovery.linkSystemContact(id, contact(numbers = listOf(PhoneDetails(E164, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE, null))))
    Recipient.live(id).refresh()

    val recipient = Recipient.resolved(id)
    assertTrue(recipient.isSystemContact)
    assertFalse(recipient.isSystemContactByPhoneNumber)
    assertEquals(E164, SignalDatabase.recipients.getRecord(id).systemPhoneE164)
  }

  @Test
  fun `linking replaces an existing link`() {
    val id = SignalDatabase.recipients.getOrInsertFromServiceId(ACI.from(UUID.randomUUID()))
    ContactDiscovery.linkSystemContact(id, contact(numbers = emptyList(), contactUri = OTHER_CONTACT_URI))

    ContactDiscovery.linkSystemContact(id, contact(numbers = emptyList()))

    assertEquals(CONTACT_URI, SignalDatabase.recipients.getRecord(id).systemContactUri)
  }

  @Test
  fun `linking a recipient that needs a link makes it linked`() {
    val id = needingLink()

    ContactDiscovery.linkSystemContact(id, contact(numbers = emptyList()))

    assertEquals(SystemContactLinkState.LINKED, SignalDatabase.recipients.getRecord(id).systemContactLinkState)
  }

  @Test
  fun `unlinking clears the link and what the contact gave the recipient`() {
    val id = SignalDatabase.recipients.getOrInsertFromServiceId(ACI.from(UUID.randomUUID()))
    ContactDiscovery.linkSystemContact(id, contact(numbers = emptyList()))

    SignalDatabase.recipients.unlinkSystemContact(id)

    val record = SignalDatabase.recipients.getRecord(id)
    assertEquals(SystemContactLinkState.NONE, record.systemContactLinkState)
    assertNull(record.systemContactUri)
    assertEquals(ProfileName.EMPTY, record.systemProfileName)
  }

  @Test
  fun `only recipients that need a link are listed`() {
    val needing = needingLink()
    val linked = SignalDatabase.recipients.getOrInsertFromServiceId(ACI.from(UUID.randomUUID()))
    ContactDiscovery.linkSystemContact(linked, contact(numbers = emptyList()))

    assertEquals(listOf(needing), SignalDatabase.recipients.getSystemContactLinksNeeded())
    assertTrue(SignalDatabase.recipients.hasSystemContactLinksNeeded())

    SignalDatabase.recipients.dismissSystemContactLinkNeeded(needing)

    assertEquals(emptyList<RecipientId>(), SignalDatabase.recipients.getSystemContactLinksNeeded())
    assertFalse(SignalDatabase.recipients.hasSystemContactLinksNeeded())
  }

  private fun needingLink(): RecipientId {
    val id = SignalDatabase.recipients.getOrInsertFromServiceId(ACI.from(UUID.randomUUID()))
    ContactDiscovery.linkSystemContact(id, contact(numbers = emptyList()))
    ContactDiscovery.refreshSystemContactLinks { LinkedContactResult.Missing }
    return id
  }

  private fun contact(numbers: List<PhoneDetails>, contactUri: String = CONTACT_URI): LinkedContact {
    return LinkedContact(
      contactUri = Uri.parse(contactUri),
      displayName = "Alice Anderson",
      givenName = "Alice",
      familyName = "Anderson",
      photoUri = PHOTO_URI,
      numbers = numbers
    )
  }

  companion object {
    private const val E164 = "+15555550101"
    private const val CONTACT_URI = "content://com.android.contacts/contacts/lookup/0r1-ABC/1"
    private const val OTHER_CONTACT_URI = "content://com.android.contacts/contacts/lookup/0r2-DEF/2"
    private const val PHOTO_URI = "content://com.android.contacts/contacts/1/photo"
  }
}
