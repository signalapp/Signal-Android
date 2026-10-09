/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.Base64
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.testutil.RecipientTestRule

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class RecipientTableTest_setSystemContactInfo {

  @get:Rule
  val recipients = RecipientTestRule()

  @Test
  fun `changing only the contact uri does not rotate the storage id`() {
    val id = createLinkedContact()
    val storageId = storageIdOf(id)

    setSystemContactInfo(id, contactUri = URI_2)

    assertThat(storageIdOf(id)).isEqualTo(storageId)
    assertThat(SignalDatabase.recipients.getRecord(id).systemContactUri).isEqualTo(URI_2)
  }

  @Test
  fun `changing the photo, label, or type rotates the storage id`() {
    val id = createLinkedContact()
    val storageId = storageIdOf(id)

    setSystemContactInfo(id, photoUri = "content://photo/2", label = "Work", type = 3)

    assertThat(storageIdOf(id)).isNotEqualTo(storageId)
  }

  @Test
  fun `changing the system name rotates the storage id`() {
    val id = createLinkedContact()
    val storageId = storageIdOf(id)

    setSystemContactInfo(id, name = ProfileName.fromParts("Alicia", "Anderson"))

    assertThat(storageIdOf(id)).isNotEqualTo(storageId)
  }

  @Test
  fun `linking a contact for the first time rotates the storage id`() {
    val id = recipients.createRecipient("Alice Anderson", profileSharing = false)
    SignalDatabase.recipients.updateStorageId(id, StorageSyncHelper.generateKey())
    val storageId = storageIdOf(id)

    setSystemContactInfo(id, contactUri = URI_1)

    assertThat(storageIdOf(id)).isNotEqualTo(storageId)
  }

  @Test
  fun `unchanged info does not rotate the storage id`() {
    val id = createLinkedContact()
    val storageId = storageIdOf(id)

    setSystemContactInfo(id)

    assertThat(storageIdOf(id)).isEqualTo(storageId)
  }

  private fun createLinkedContact(): RecipientId {
    val id = recipients.createRecipient("Alice Anderson", profileSharing = false)
    setSystemContactInfo(id)
    SignalDatabase.recipients.updateStorageId(id, StorageSyncHelper.generateKey())
    return id
  }

  private fun setSystemContactInfo(
    id: RecipientId,
    name: ProfileName = ProfileName.fromParts("Alice", "Anderson"),
    photoUri: String? = "content://photo/1",
    label: String? = null,
    type: Int = 2,
    contactUri: String = URI_1
  ) {
    val handle = SignalDatabase.recipients.beginBulkSystemContactUpdate(clearInfoForMissingContacts = false)
    try {
      handle.setSystemContactInfo(
        id = id,
        systemProfileName = name,
        systemDisplayName = name.toString(),
        photoUri = photoUri,
        systemPhoneLabel = label,
        systemPhoneType = type,
        systemContactUri = contactUri
      )
    } finally {
      handle.finish()
    }
  }

  private fun storageIdOf(id: RecipientId): String? {
    return SignalDatabase.recipients.getRecord(id).storageId?.let { Base64.encodeWithPadding(it) }
  }

  companion object {
    private const val URI_1 = "content://com.android.contacts/contacts/lookup/abc/10"
    private const val URI_2 = "content://com.android.contacts/contacts/lookup/abc/11"
  }
}
