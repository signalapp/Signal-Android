/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.app.Application
import android.provider.ContactsContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.archive.proto.Contact
import org.signal.core.models.ServiceId.ACI
import org.thoughtcrime.securesms.backup.v2.importer.ContactArchiveImporter
import org.thoughtcrime.securesms.database.RecipientTable.SystemContactLinkState
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import org.whispersystems.signalservice.api.storage.SignalContactRecord
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord
import java.util.UUID

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class RecipientTableTest_systemContactLinkNeededOnRestore {

  @get:Rule
  val recipients = RecipientTestRule()

  @Test
  fun `a restored link needs to be made again and keeps its name`() {
    val linked = SignalDatabase.recipients.getOrInsertFromE164(E164)
    link(linked)
    val unlinked = SignalDatabase.recipients.getOrInsertFromE164(OTHER_E164)

    SignalDatabase.recipients.markSystemContactLinksNeededPostBackupRestore()

    val record = SignalDatabase.recipients.getRecord(linked)
    assertEquals(SystemContactLinkState.NEEDED, record.systemContactLinkState)
    assertNull(record.systemContactUri)
    assertNull(record.systemContactPhotoUri)
    assertEquals(ProfileName.fromParts("Alice", "Anderson"), record.systemProfileName)

    assertEquals(SystemContactLinkState.NONE, SignalDatabase.recipients.getRecord(unlinked).systemContactLinkState)
  }

  @Test
  fun `a storage service contact with a system name needs a link`() {
    val aci = ACI.from(UUID.randomUUID())
    insertStorageContact(aci, systemGivenName = "Alice")

    assertEquals(SystemContactLinkState.NEEDED, SignalDatabase.recipients.getRecord(SignalDatabase.recipients.getByAci(aci).get()).systemContactLinkState)
  }

  @Test
  fun `a storage service contact without a system name does not need a link`() {
    val aci = ACI.from(UUID.randomUUID())
    insertStorageContact(aci, systemGivenName = "")

    assertEquals(SystemContactLinkState.NONE, SignalDatabase.recipients.getRecord(SignalDatabase.recipients.getByAci(aci).get()).systemContactLinkState)
  }

  @Test
  fun `a backup contact with a system name needs a link`() {
    val id = ContactArchiveImporter.import(backupContact(systemGivenName = "Alice"))!!

    assertEquals(SystemContactLinkState.NEEDED, SignalDatabase.recipients.getRecord(id).systemContactLinkState)
  }

  @Test
  fun `a backup contact without a system name does not need a link`() {
    val id = ContactArchiveImporter.import(backupContact(systemGivenName = ""))!!

    assertEquals(SystemContactLinkState.NONE, SignalDatabase.recipients.getRecord(id).systemContactLinkState)
  }

  private fun link(id: RecipientId) {
    val handle = SignalDatabase.recipients.beginBulkSystemContactUpdate()
    try {
      handle.setSystemContactInfo(
        id = id,
        systemProfileName = ProfileName.fromParts("Alice", "Anderson"),
        systemDisplayName = "Alice Anderson",
        photoUri = "content://com.android.contacts/contacts/1/photo",
        systemPhoneLabel = null,
        systemPhoneType = ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
        systemPhoneE164 = E164,
        systemContactUri = "content://com.android.contacts/contacts/lookup/0r1-ABC/1"
      )
    } finally {
      handle.finish()
    }
  }

  private fun insertStorageContact(aci: ACI, systemGivenName: String) {
    val record = SignalContactRecord(
      id = StorageId.forContact(byteArrayOf(1, 2, 3, 4)),
      proto = ContactRecord(
        aciBinary = aci.toByteString(),
        systemGivenName = systemGivenName
      )
    )
    SignalDatabase.recipients.applyStorageSyncContactInsert(record, rotateProfileKeyOnBlock = false)
  }

  private fun backupContact(systemGivenName: String): Contact {
    return Contact(
      aci = ACI.from(UUID.randomUUID()).toByteString(),
      registered = Contact.Registered(),
      systemGivenName = systemGivenName
    )
  }

  companion object {
    private const val E164 = "+15555550101"
    private const val OTHER_E164 = "+15555550202"
  }
}
