/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.storage

import android.app.Application
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.database.delete
import org.thoughtcrime.securesms.database.GroupTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.testutil.RecipientTestRule

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class StorageSyncModelsTest_pinnedConversations {

  @get:Rule
  val recipients = RecipientTestRule()

  @Test
  fun givenAPinnedGroupWithAMasterKey_whenIConvertPins_thenIIncludeTheMasterKey() {
    // GIVEN
    val alice = recipients.createRecipient("Alice Android")
    val group = recipients.createGroup(alice)
    val record = SignalDatabase.recipients.getRecordForSync(group.recipientId)!!

    // WHEN
    val pins = StorageSyncModels.localToRemotePinnedConversations(listOf(record))

    // THEN
    assertEquals(listOf(group.masterKey.serialize().toByteString()), pins.map { it.groupMasterKey })
  }

  @Test
  fun givenAPinnedGroupWithNoGroupEntry_whenIConvertPins_thenILeaveItOutAndKeepTheOthers() {
    // GIVEN
    val alice = recipients.createRecipient("Alice Android")
    val group = recipients.createGroup(alice)
    SignalDatabase.groups.writableDatabase
      .delete(GroupTable.TABLE_NAME)
      .where("${GroupTable.GROUP_ID} = ?", group.groupId.toString())
      .run()

    val aliceRecord = SignalDatabase.recipients.getRecordForSync(alice)!!
    val groupRecord = SignalDatabase.recipients.getRecordForSync(group.recipientId)!!

    // WHEN
    val pins = StorageSyncModels.localToRemotePinnedConversations(listOf(aliceRecord, groupRecord))

    // THEN
    assertEquals(listOf(aliceRecord.serviceId!!.toByteString()), pins.map { it.contact?.serviceIdBinary })
  }
}
