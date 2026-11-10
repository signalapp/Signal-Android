/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.app.Application
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.recipients.RecipientId
import org.signal.core.util.CursorUtil
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.thoughtcrime.securesms.components.settings.app.chats.folders.ChatFolderRecord
import org.thoughtcrime.securesms.conversationlist.model.ConversationFilter
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.api.storage.toSignalAccountRecord
import org.whispersystems.signalservice.internal.storage.protos.AccountRecord
import kotlin.random.Random

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class ThreadTableTest_pinned {

  @get:Rule
  val recipients = RecipientTestRule()

  private lateinit var recipient: RecipientId
  private val allChats: ChatFolderRecord = ChatFolderRecord(folderType = ChatFolderRecord.FolderType.ALL)

  @Before
  fun setUp() {
    recipient = recipients.createRecipient("Alice Android")
  }

  @Test
  fun givenAPinnedThread_whenIDeleteTheLastMessage_thenIDoNotDeleteOrUnpinTheThread() {
    // GIVEN
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(recipient))
    val messageId = recipients.insertOutgoingMessage(recipient)
    SignalDatabase.threads.pinConversations(listOf(threadId))

    // WHEN
    SignalDatabase.messages.deleteMessage(messageId)

    // THEN
    val pinned = SignalDatabase.threads.getPinnedThreadIds()
    assertTrue(threadId in pinned)
  }

  @Test
  fun givenAPinnedThread_whenIDeleteTheLastMessage_thenIExpectTheThreadInUnarchivedCount() {
    // GIVEN
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(recipient))
    val messageId = recipients.insertOutgoingMessage(recipient)
    SignalDatabase.threads.pinConversations(listOf(threadId))

    // WHEN
    SignalDatabase.messages.deleteMessage(messageId)

    // THEN
    val unarchivedCount = SignalDatabase.threads.getUnarchivedConversationListCount(ConversationFilter.OFF, allChats)
    assertEquals(1, unarchivedCount)
  }

  @Test
  fun givenAPinnedThread_whenIDeleteTheLastMessage_thenIExpectPinnedThreadInUnarchivedList() {
    // GIVEN
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(recipient))
    val messageId = recipients.insertOutgoingMessage(recipient)
    SignalDatabase.threads.pinConversations(listOf(threadId))

    // WHEN
    SignalDatabase.messages.deleteMessage(messageId)

    // THEN
    SignalDatabase.threads.getUnarchivedConversationList(ConversationFilter.OFF, true, 0, 1, allChats).use {
      it.moveToFirst()
      assertEquals(threadId, CursorUtil.requireLong(it, ThreadTable.ID))
    }
  }

  @Test
  fun givenARemotePinForAnUnknownGroup_whenIApplyTheAccountRecord_thenIDoNotPinItOrCreateARecipient() {
    // GIVEN
    val masterKey = GroupMasterKey(Random.nextBytes(GroupMasterKey.SIZE))
    val groupId = GroupId.v2(masterKey)

    // WHEN
    SignalDatabase.threads.applyStorageSyncUpdate(recipients.self, accountRecordPinning(masterKey))

    // THEN
    assertTrue(SignalDatabase.threads.getPinnedRecipientIds().isEmpty())
    assertFalse(SignalDatabase.recipients.getByGroupId(groupId).isPresent)
  }

  @Test
  fun givenARemotePinForAKnownGroup_whenIApplyTheAccountRecord_thenIPinIt() {
    // GIVEN
    val group = recipients.createGroup(recipient)

    // WHEN
    SignalDatabase.threads.applyStorageSyncUpdate(recipients.self, accountRecordPinning(group.masterKey))

    // THEN
    assertEquals(listOf(group.recipientId), SignalDatabase.threads.getPinnedRecipientIds())
  }

  @Test
  fun givenAPinnedThread_whenIDeleteTheConversation_thenSelfNeedsSync() {
    // GIVEN
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(recipient))
    recipients.insertOutgoingMessage(recipient)
    SignalDatabase.threads.pinConversations(listOf(threadId))
    val selfStorageIdBefore = selfStorageId()

    // WHEN
    SignalDatabase.threads.deleteConversations(setOf(threadId), syncThreadDeletes = false)

    // THEN
    assertTrue(SignalDatabase.threads.getPinnedThreadIds().isEmpty())
    assertNotEquals(selfStorageIdBefore, selfStorageId())
  }

  @Test
  fun givenAnUnpinnedThread_whenIDeleteTheConversation_thenSelfDoesNotNeedSync() {
    // GIVEN
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(recipient))
    recipients.insertOutgoingMessage(recipient)
    val selfStorageIdBefore = selfStorageId()

    // WHEN
    SignalDatabase.threads.deleteConversations(setOf(threadId), syncThreadDeletes = false)

    // THEN
    assertEquals(selfStorageIdBefore, selfStorageId())
  }

  @Test
  fun givenAPinnedThread_whenIArchiveIt_thenSelfNeedsSync() {
    // GIVEN
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(recipient))
    recipients.insertOutgoingMessage(recipient)
    SignalDatabase.threads.pinConversations(listOf(threadId))
    val selfStorageIdBefore = selfStorageId()

    // WHEN
    SignalDatabase.threads.setArchived(setOf(threadId), archive = true)

    // THEN
    assertTrue(SignalDatabase.threads.getPinnedThreadIds().isEmpty())
    assertNotEquals(selfStorageIdBefore, selfStorageId())
  }

  @Test
  fun givenAnUnpinnedThread_whenIArchiveIt_thenSelfDoesNotNeedSync() {
    // GIVEN
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(recipient))
    recipients.insertOutgoingMessage(recipient)
    val selfStorageIdBefore = selfStorageId()

    // WHEN
    SignalDatabase.threads.setArchived(setOf(threadId), archive = true)

    // THEN
    assertEquals(selfStorageIdBefore, selfStorageId())
  }

  private fun accountRecordPinning(masterKey: GroupMasterKey) = AccountRecord(
    pinnedConversations = listOf(
      AccountRecord.PinnedConversation(groupMasterKey = masterKey.serialize().toByteString())
    )
  ).toSignalAccountRecord(StorageId.forAccount(Random.nextBytes(16)))

  private fun selfStorageId(): String? {
    return SignalDatabase.recipients.getRecordForSync(recipients.self)?.storageId?.toByteString()?.base64()
  }
}
