/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.util

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import org.thoughtcrime.securesms.transport.UndeliverableMessageException
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage
import org.whispersystems.signalservice.internal.push.Content
import org.whispersystems.signalservice.internal.push.DataMessage
import org.whispersystems.signalservice.internal.push.EditMessage
import org.whispersystems.signalservice.internal.push.GroupContextV2
import org.whispersystems.signalservice.internal.push.SyncMessage

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class GroupUtilTest {

  @get:Rule
  val recipients = RecipientTestRule()

  @Test
  fun setDataMessageGroupContext_attachesContextForHealthyGroup() {
    val group = recipients.createGroup(recipients.createRecipient("Member One"))
    val builder = SignalServiceDataMessage.newBuilder().withTimestamp(1L)

    GroupUtil.setDataMessageGroupContext(ApplicationProvider.getApplicationContext(), builder, group.groupId.requirePush())

    assertTrue(builder.build().groupContext.isPresent)
  }

  @Test(expected = UndeliverableMessageException::class)
  fun setDataMessageGroupContext_throwsWhenGroupPropertiesMissing() {
    val group = recipients.createGroup(recipients.createRecipient("Member One"))

    // Multi-device keeps the group stub but strips its V2 properties once left and deleted.
    every { recipients.signalStore.account.isMultiDevice } returns true
    SignalDatabase.groups.setMember(group.groupId, false)
    SignalDatabase.groups.clearGroupIfLeftAndDeleted(group.groupId)

    val builder = SignalServiceDataMessage.newBuilder().withTimestamp(1L)
    GroupUtil.setDataMessageGroupContext(ApplicationProvider.getApplicationContext(), builder, group.groupId.requirePush())
  }

  @Test
  fun getGroupContextIfPresent_returnsContextForGroupEditMessage() {
    val content = Content(editMessage = EditMessage(targetSentTimestamp = 1L, dataMessage = DataMessage(body = "edit", groupV2 = GROUP_CONTEXT)))

    assertEquals(GROUP_CONTEXT, GroupUtil.getGroupContextIfPresent(content))
  }

  @Test
  fun getGroupContextIfPresent_returnsContextForSyncedGroupEditMessage() {
    val editMessage = EditMessage(targetSentTimestamp = 1L, dataMessage = DataMessage(body = "edit", groupV2 = GROUP_CONTEXT))
    val content = Content(syncMessage = SyncMessage(sent = SyncMessage.Sent(timestamp = 2L, editMessage = editMessage)))

    assertEquals(GROUP_CONTEXT, GroupUtil.getGroupContextIfPresent(content))
  }

  @Test
  fun getGroupContextIfPresent_returnsNullForIndividualEditMessage() {
    val content = Content(editMessage = EditMessage(targetSentTimestamp = 1L, dataMessage = DataMessage(body = "edit")))

    assertNull(GroupUtil.getGroupContextIfPresent(content))
  }

  companion object {
    private val GROUP_CONTEXT = GroupContextV2(masterKey = ByteArray(32) { 1 }.toByteString(), revision = 3)
  }
}
