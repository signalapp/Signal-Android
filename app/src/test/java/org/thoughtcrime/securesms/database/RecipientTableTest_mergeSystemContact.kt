/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.app.Application
import android.provider.ContactsContract
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.ServiceId.ACI
import org.thoughtcrime.securesms.database.RecipientTable.SystemContactLinkState
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import java.util.UUID

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class RecipientTableTest_mergeSystemContact {

  @get:Rule
  val recipients = RecipientTestRule()

  @Test
  fun `a merge keeps every system contact field of the linked record`() {
    link(SignalDatabase.recipients.getOrInsertFromE164(E164), CONTACT_URI, systemPhoneE164 = E164)
    val aci = ACI.from(UUID.randomUUID())
    SignalDatabase.recipients.getOrInsertFromServiceId(aci)

    val merged = SignalDatabase.recipients.getRecord(SignalDatabase.recipients.getAndPossiblyMerge(aci, E164))

    assertEquals(ProfileName.fromParts("Alice", "Anderson"), merged.systemProfileName)
    assertEquals(PHOTO_URI, merged.systemContactPhotoUri)
    assertEquals("Cell", merged.systemPhoneLabel)
    assertEquals(ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM, merged.systemPhoneType)
    assertEquals(E164, merged.systemPhoneE164)
    assertEquals(CONTACT_URI, merged.systemContactUri)
    assertEquals(SystemContactLinkState.LINKED, merged.systemContactLinkState)
  }

  @Test
  fun `a merge keeps the link of the ACI record when the E164 record has none`() {
    val aci = ACI.from(UUID.randomUUID())
    link(SignalDatabase.recipients.getOrInsertFromServiceId(aci), CONTACT_URI, systemPhoneE164 = null)
    SignalDatabase.recipients.getOrInsertFromE164(E164)

    val merged = SignalDatabase.recipients.getRecord(SignalDatabase.recipients.getAndPossiblyMerge(aci, E164))

    assertEquals(CONTACT_URI, merged.systemContactUri)
    assertEquals(SystemContactLinkState.LINKED, merged.systemContactLinkState)
  }

  @Test
  fun `a merge of two linked records keeps the link of the E164 record`() {
    val aci = ACI.from(UUID.randomUUID())
    link(SignalDatabase.recipients.getOrInsertFromServiceId(aci), OTHER_CONTACT_URI, systemPhoneE164 = null)
    link(SignalDatabase.recipients.getOrInsertFromE164(E164), CONTACT_URI, systemPhoneE164 = E164)

    val merged = SignalDatabase.recipients.getRecord(SignalDatabase.recipients.getAndPossiblyMerge(aci, E164))

    assertEquals(CONTACT_URI, merged.systemContactUri)
    assertEquals(E164, merged.systemPhoneE164)
  }

  private fun link(id: RecipientId, contactUri: String, systemPhoneE164: String?) {
    val handle = SignalDatabase.recipients.beginBulkSystemContactUpdate()
    try {
      handle.setSystemContactInfo(
        id = id,
        systemProfileName = ProfileName.fromParts("Alice", "Anderson"),
        systemDisplayName = "Alice Anderson",
        photoUri = PHOTO_URI,
        systemPhoneLabel = "Cell",
        systemPhoneType = ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM,
        systemPhoneE164 = systemPhoneE164,
        systemContactUri = contactUri
      )
    } finally {
      handle.finish()
    }
  }

  companion object {
    private const val E164 = "+15555550101"
    private const val CONTACT_URI = "content://com.android.contacts/contacts/lookup/0r1-ABC/1"
    private const val OTHER_CONTACT_URI = "content://com.android.contacts/contacts/lookup/0r2-DEF/2"
    private const val PHOTO_URI = "content://com.android.contacts/contacts/1/photo"
  }
}
