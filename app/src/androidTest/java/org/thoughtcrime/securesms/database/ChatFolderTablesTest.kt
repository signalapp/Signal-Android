/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.deleteAll
import org.thoughtcrime.securesms.components.settings.app.chats.folders.ChatFolderRecord
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testing.SignalActivityRule

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

  private var aliceThread: Long = 0
  private var bobThread: Long = 0
  private var charlieThread: Long = 0

  @Before
  fun setUp() {
    alice = harness.others[1]
    bob = harness.others[2]
    charlie = harness.others[3]

    aliceThread = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(alice))
    bobThread = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(bob))
    charlieThread = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(charlie))

    folder1 = ChatFolderRecord(
      id = 2,
      name = "folder1",
      position = 1,
      includedChats = listOf(aliceThread, bobThread),
      excludedChats = listOf(charlieThread),
      showUnread = true,
      showMutedChats = true,
      showIndividualChats = true,
      folderType = ChatFolderRecord.FolderType.CUSTOM
    )

    folder2 = ChatFolderRecord(
      name = "folder2",
      includedChats = listOf(bobThread),
      showUnread = true,
      showMutedChats = true,
      showIndividualChats = true,
      folderType = ChatFolderRecord.FolderType.INDIVIDUAL
    )

    folder3 = ChatFolderRecord(
      name = "folder3",
      includedChats = listOf(bobThread),
      excludedChats = listOf(aliceThread, charlieThread),
      showUnread = true,
      showMutedChats = true,
      showGroupChats = true,
      isMuted = true,
      folderType = ChatFolderRecord.FolderType.GROUP
    )

    SignalDatabase.chatFolders.writableDatabase.deleteAll(ChatFolderTables.ChatFolderTable.TABLE_NAME)
    SignalDatabase.chatFolders.writableDatabase.deleteAll(ChatFolderTables.ChatFolderMembershipTable.TABLE_NAME)
  }

  @Test
  fun givenChatFolder_whenIGetFolder_thenIExpectFolderWithChats() {
    SignalDatabase.chatFolders.createFolder(folder1)
    val actualFolders = SignalDatabase.chatFolders.getChatFolders()

    assertEquals(listOf(folder1), actualFolders)
  }

  @Test
  fun givenChatFolder_whenIUpdateFolder_thenIExpectUpdatedFolderWithChats() {
    SignalDatabase.chatFolders.createFolder(folder2)
    val folder = SignalDatabase.chatFolders.getChatFolders().first()
    val updatedFolder = folder.copy(
      name = "updatedFolder2",
      position = 1,
      isMuted = true,
      includedChats = listOf(aliceThread, charlieThread),
      excludedChats = listOf(bobThread)
    )
    SignalDatabase.chatFolders.updateFolder(updatedFolder)

    val actualFolder = SignalDatabase.chatFolders.getChatFolders().first()

    assertEquals(updatedFolder, actualFolder)
  }

  @Test
  fun givenADeletedChatFolder_whenIGetFolders_thenIExpectAListWithoutThatFolder() {
    SignalDatabase.chatFolders.createFolder(folder1)
    SignalDatabase.chatFolders.createFolder(folder2)
    val folders = SignalDatabase.chatFolders.getChatFolders()
    SignalDatabase.chatFolders.deleteChatFolder(folders.last())

    val actualFolders = SignalDatabase.chatFolders.getChatFolders()

    assertEquals(listOf(folder1), actualFolders)
  }
}
