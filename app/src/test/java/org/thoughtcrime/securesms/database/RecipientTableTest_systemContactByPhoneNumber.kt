/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.app.Application
import android.provider.ContactsContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.ServiceId.ACI
import org.signal.core.util.CursorUtil
import org.thoughtcrime.securesms.database.RecipientTable.PhoneNumberSharingState
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import java.util.UUID

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class RecipientTableTest_systemContactByPhoneNumber {

  @get:Rule
  val recipients = RecipientTestRule()

  @Test
  fun `a contact linked by its number counts`() {
    val id = SignalDatabase.recipients.getOrInsertFromE164(E164)
    link(id)

    assertTrue(Recipient.resolved(id).isSystemContactByPhoneNumber)
  }

  @Test
  fun `an undiscoverable recipient does not count`() {
    val id = SignalDatabase.recipients.getOrInsertFromE164(E164)
    link(id)
    SignalDatabase.recipients.updatePhoneNumberDiscoverability(presentInCds = emptySet(), missingFromCds = setOf(id))
    Recipient.live(id).refresh()

    val recipient = Recipient.resolved(id)
    assertTrue(recipient.isSystemContact)
    assertFalse(recipient.isSystemContactByPhoneNumber)
  }

  @Test
  fun `digit search matches the hidden number of a recipient whose contact holds it`() {
    val id = SignalDatabase.recipients.getOrInsertFromE164(E164)
    SignalDatabase.recipients.setPhoneNumberSharing(id, PhoneNumberSharingState.DISABLED)
    link(id)

    assertTrue(id in searchAllContacts("5550101"))
  }

  @Test
  fun `digit search does not match the hidden number of a recipient whose contact lacks it`() {
    val id = SignalDatabase.recipients.getOrInsertFromE164(E164)
    SignalDatabase.recipients.setPhoneNumberSharing(id, PhoneNumberSharingState.DISABLED)
    link(id, systemPhoneE164 = null)

    assertFalse(id in searchAllContacts("5550101"))
  }

  @Test
  fun `a recipient who changes numbers no longer counts`() {
    val aci = ACI.from(UUID.randomUUID())
    val id = SignalDatabase.recipients.getAndPossiblyMerge(aci, E164)
    SignalDatabase.recipients.setPhoneNumberSharing(id, PhoneNumberSharingState.DISABLED)
    link(id)

    SignalDatabase.recipients.getAndPossiblyMerge(aci, NEW_E164)
    Recipient.live(id).refresh()

    val recipient = Recipient.resolved(id)
    assertEquals(NEW_E164, recipient.requireE164())
    assertTrue(recipient.isSystemContact)
    assertFalse(recipient.isSystemContactByPhoneNumber)
    assertFalse(recipient.shouldShowE164)
    assertFalse(id in searchAllContacts("5550202"))
  }

  private fun link(id: RecipientId, systemPhoneE164: String? = Recipient.resolved(id).requireE164()) {
    val handle = SignalDatabase.recipients.beginBulkSystemContactUpdate(clearInfoForMissingContacts = false)
    try {
      handle.setSystemContactInfo(
        id = id,
        systemProfileName = ProfileName.fromParts("Alice", "Anderson"),
        systemDisplayName = "Alice Anderson",
        photoUri = null,
        systemPhoneLabel = null,
        systemPhoneType = ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
        systemPhoneE164 = systemPhoneE164,
        systemContactUri = "content://com.android.contacts/contacts/lookup/0r1-ABC/1"
      )
    } finally {
      handle.finish()
    }
    Recipient.live(id).refresh()
  }

  private fun searchAllContacts(query: String): Set<RecipientId> {
    return SignalDatabase.recipients.queryAllContacts(query, RecipientTable.IncludeSelfMode.Exclude)!!.use { cursor ->
      val ids = mutableSetOf<RecipientId>()
      while (cursor.moveToNext()) {
        ids.add(RecipientId.from(CursorUtil.requireLong(cursor, RecipientTable.ID)))
      }
      ids
    }
  }

  companion object {
    private const val E164 = "+15555550101"
    private const val NEW_E164 = "+15555550202"
  }
}
