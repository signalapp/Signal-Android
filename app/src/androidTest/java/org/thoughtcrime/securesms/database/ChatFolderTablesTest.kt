/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.deleteAll
import org.thoughtcrime.securesms.components.settings.app.chats.folders.ChatFolderId
import org.thoughtcrime.securesms.components.settings.app.chats.folders.ChatFolderRecord
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.testing.SignalActivityRule
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.api.storage.SignalChatFolderRecord
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.api.util.UuidUtil
import java.util.UUID
import org.whispersystems.signalservice.internal.storage.protos.ChatFolderRecord as RemoteChatFolderRecord
import org.whispersystems.signalservice.internal.storage.protos.Recipient as RemoteRecipient

@RunWith(AndroidJUnit4::class)
class ChatFolderTablesTest {

  @get:Rule
  val harness = SignalActivityRule()

  private lateinit var alice: RecipientId
  private lateinit var bob: RecipientId
  private lateinit var charlie: RecipientId

  private lateinit var folder1: ChatFolderRecord
  private lateinit var folder2: ChatFolderRecord
  private lateinit var folder3: ChatFolderRecord

  private lateinit var recipientIds: List<RecipientId>

  private var aliceThread: Long = 0
  private var bobThread: Long = 0
  private var charlieThread: Long = 0

  @Before
  fun setUp() {
    recipientIds = createRecipients(5)

    alice = recipientIds[0]
    bob = recipientIds[1]
    charlie = recipientIds[2]

    aliceThread = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(alice))
    bobThread = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(bob))
    charlieThread = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(charlie))

    folder1 = ChatFolderRecord(
      id = 2,
      name = "folder1",
      position = 0,
      includedChats = listOf(aliceThread, bobThread),
      excludedChats = listOf(charlieThread),
      showUnread = true,
      showMutedChats = true,
      showIndividualChats = true,
      folderType = ChatFolderRecord.FolderType.CUSTOM,
      chatFolderId = ChatFolderId.generate(),
      storageServiceId = StorageId.forChatFolder(byteArrayOf(1, 2, 3))
    )

    folder2 = ChatFolderRecord(
      name = "folder2",
      position = 2,
      includedChats = listOf(bobThread),
      showUnread = true,
      showMutedChats = true,
      showIndividualChats = true,
      folderType = ChatFolderRecord.FolderType.INDIVIDUAL,
      chatFolderId = ChatFolderId.generate(),
      storageServiceId = StorageId.forChatFolder(byteArrayOf(2, 3, 4))
    )

    folder3 = ChatFolderRecord(
      name = "folder3",
      position = 3,
      includedChats = listOf(bobThread),
      excludedChats = listOf(aliceThread, charlieThread),
      showUnread = true,
      showMutedChats = true,
      showGroupChats = true,
      folderType = ChatFolderRecord.FolderType.GROUP,
      chatFolderId = ChatFolderId.generate(),
      storageServiceId = StorageId.forChatFolder(byteArrayOf(3, 4, 5))
    )

    SignalDatabase.chatFolders.writableDatabase.deleteAll(ChatFolderTables.ChatFolderTable.TABLE_NAME)
    SignalDatabase.chatFolders.writableDatabase.deleteAll(ChatFolderTables.ChatFolderMembershipTable.TABLE_NAME)
  }

  @Test
  fun givenChatFolder_whenIGetFolder_thenIExpectFolderWithChats() {
    SignalDatabase.chatFolders.createFolder(folder1)
    val actualFolders = SignalDatabase.chatFolders.getCurrentChatFolders()

    assertEquals(listOf(folder1), actualFolders)
  }

  @Test
  fun givenChatFolder_whenIUpdateFolder_thenIExpectUpdatedFolderWithChats() {
    SignalDatabase.chatFolders.createFolder(folder2)
    val folder = SignalDatabase.chatFolders.getCurrentChatFolders().first()
    val updatedFolder = folder.copy(
      name = "updatedFolder2",
      position = 1,
      includedChats = listOf(aliceThread, charlieThread),
      excludedChats = listOf(bobThread)
    )
    SignalDatabase.chatFolders.updateFolder(updatedFolder)

    val actualFolder = SignalDatabase.chatFolders.getCurrentChatFolders().first()

    assertEquals(updatedFolder, actualFolder)
  }

  @Test
  fun givenADeletedChatFolder_whenIGetFolders_thenIExpectAListWithoutThatFolder() {
    SignalDatabase.chatFolders.createFolder(folder1)
    SignalDatabase.chatFolders.createFolder(folder2)
    val folders = SignalDatabase.chatFolders.getCurrentChatFolders()
    SignalDatabase.chatFolders.deleteChatFolder(folders.last())

    val actualFolders = SignalDatabase.chatFolders.getCurrentChatFolders()

    assertEquals(listOf(folder1), actualFolders)
  }

  @Test
  fun givenChatFolders_whenIUpdateTheirStorageSyncIds_thenIExpectAnUpdatedList() {
    val existingMap = SignalDatabase.chatFolders.getStorageSyncIdsMap()
    existingMap.forEach { (id, _) ->
      SignalDatabase.chatFolders.applyStorageIdUpdate(id, StorageId.forChatFolder(StorageSyncHelper.generateKey()))
    }
    val updatedMap = SignalDatabase.chatFolders.getStorageSyncIdsMap()

    existingMap.forEach { (id, storageId) ->
      assertNotEquals(storageId, updatedMap[id])
    }
  }

  @Test
  fun givenARemoteFolder_whenIInsertLocally_thenIExpectAListWithThatFolder() {
    val remoteRecord =
      SignalChatFolderRecord(
        folder1.storageServiceId!!,
        RemoteChatFolderRecord(
          identifier = UuidUtil.toByteArray(folder1.chatFolderId.uuid).toByteString(),
          name = folder1.name,
          position = folder1.position,
          showOnlyUnread = folder1.showUnread,
          showMutedChats = folder1.showMutedChats,
          includeAllIndividualChats = folder1.showIndividualChats,
          includeAllGroupChats = folder1.showGroupChats,
          folderType = RemoteChatFolderRecord.FolderType.CUSTOM,
          deletedAtTimestampMs = folder1.deletedTimestampMs,
          includedRecipients = listOf(
            RemoteRecipient(RemoteRecipient.Contact(Recipient.resolved(alice).serviceId.get().toString())),
            RemoteRecipient(RemoteRecipient.Contact(Recipient.resolved(bob).serviceId.get().toString()))
          ),
          excludedRecipients = listOf(
            RemoteRecipient(RemoteRecipient.Contact(Recipient.resolved(charlie).serviceId.get().toString()))
          )

        )
      )

    SignalDatabase.chatFolders.insertChatFolderFromStorageSync(remoteRecord)
    val actualFolders = SignalDatabase.chatFolders.getCurrentChatFolders()

    assertEquals(listOf(folder1), actualFolders)
  }

  @Test
  fun givenADeletedChatFolder_whenIGetPositions_thenIExpectPositionsToStillBeConsecutive() {
    SignalDatabase.chatFolders.createFolder(folder1)
    SignalDatabase.chatFolders.createFolder(folder2)
    SignalDatabase.chatFolders.createFolder(folder3)

    val folders = SignalDatabase.chatFolders.getCurrentChatFolders()
    SignalDatabase.chatFolders.deleteChatFolder(folders[1])

    val actualFolders = SignalDatabase.chatFolders.getCurrentChatFolders()
    actualFolders.forEachIndexed { index, folder ->
      assertEquals(folder.position, index)
    }
  }

  private fun createRecipients(count: Int): List<RecipientId> {
    return (1..count).map {
      SignalDatabase.recipients.getOrInsertFromServiceId(ACI.from(UUID.randomUUID()))
    }
  }
}
