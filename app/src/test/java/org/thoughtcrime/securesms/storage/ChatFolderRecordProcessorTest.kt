package org.thoughtcrime.securesms.storage

import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.testutil.EmptyLogger
import org.whispersystems.signalservice.api.storage.SignalChatFolderRecord
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.api.util.UuidUtil
import org.whispersystems.signalservice.internal.storage.protos.ChatFolderRecord
import java.util.UUID

/**
 * Tests for [ChatFolderRecordProcessor]
 */
class ChatFolderRecordProcessorTest {
  companion object {
    val STORAGE_ID: StorageId = StorageId.forChatFolder(byteArrayOf(1, 2, 3, 4))

    @JvmStatic
    @BeforeClass
    fun setUpClass() {
      Log.initialize(EmptyLogger())
    }
  }

  private val testSubject = ChatFolderRecordProcessor()

  @Test
  fun `Given a valid proto with a known name and folder type, assert valid`() {
    // GIVEN
    val proto = ChatFolderRecord.Builder().apply {
      identifier = UuidUtil.toByteArray(UUID.randomUUID()).toByteString()
      name = "Folder1"
      position = 1
      showOnlyUnread = false
      showMutedChats = false
      includeAllIndividualChats = false
      includeAllGroupChats = false
      folderType = ChatFolderRecord.FolderType.CUSTOM
      deletedAtTimestampMs = 0L
    }.build()
    val record = SignalChatFolderRecord(STORAGE_ID, proto)

    // WHEN
    val result = testSubject.isInvalid(record)

    // THEN
    assertFalse(result)
  }

  @Test
  fun `Given an invalid proto with no name, assert invalid`() {
    // GIVEN
    val proto = ChatFolderRecord.Builder().apply {
      identifier = UuidUtil.toByteArray(UUID.randomUUID()).toByteString()
      name = ""
      position = 1
      showOnlyUnread = false
      showMutedChats = false
      includeAllIndividualChats = false
      includeAllGroupChats = false
      folderType = ChatFolderRecord.FolderType.CUSTOM
      deletedAtTimestampMs = 0L
    }.build()
    val record = SignalChatFolderRecord(STORAGE_ID, proto)

    // WHEN
    val result = testSubject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `Given a valid proto with no folder type, assert invalid`() {
    // GIVEN
    val proto = ChatFolderRecord.Builder().apply {
      identifier = UuidUtil.toByteArray(UUID.randomUUID()).toByteString()
      name = "Folder1"
      position = 1
      showOnlyUnread = false
      showMutedChats = false
      includeAllIndividualChats = false
      includeAllGroupChats = false
      folderType = ChatFolderRecord.FolderType.UNKNOWN
      deletedAtTimestampMs = 0L
    }.build()
    val record = SignalChatFolderRecord(STORAGE_ID, proto)

    // WHEN
    val result = testSubject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `Given a valid proto with a deleted timestamp and negative position, assert valid`() {
    // GIVEN
    val proto = ChatFolderRecord.Builder().apply {
      identifier = UuidUtil.toByteArray(UUID.randomUUID()).toByteString()
      name = "Folder1"
      position = -1
      showOnlyUnread = false
      showMutedChats = false
      includeAllIndividualChats = false
      includeAllGroupChats = false
      folderType = ChatFolderRecord.FolderType.CUSTOM
      deletedAtTimestampMs = 100L
    }.build()
    val record = SignalChatFolderRecord(STORAGE_ID, proto)

    // WHEN
    val result = testSubject.isInvalid(record)

    // THEN
    assertFalse(result)
  }

  @Test
  fun `Given an invalid proto with a deleted timestamp and positive position, assert invalid`() {
    // GIVEN
    val proto = ChatFolderRecord.Builder().apply {
      identifier = UuidUtil.toByteArray(UUID.randomUUID()).toByteString()
      name = "Folder1"
      position = 1
      showOnlyUnread = false
      showMutedChats = false
      includeAllIndividualChats = false
      includeAllGroupChats = false
      folderType = ChatFolderRecord.FolderType.CUSTOM
      deletedAtTimestampMs = 100L
    }.build()
    val record = SignalChatFolderRecord(STORAGE_ID, proto)

    // WHEN
    val result = testSubject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `Given an invalid proto with a negative position, assert invalid`() {
    // GIVEN
    val proto = ChatFolderRecord.Builder().apply {
      identifier = UuidUtil.toByteArray(UUID.randomUUID()).toByteString()
      name = "Folder1"
      position = -1
      showOnlyUnread = false
      showMutedChats = false
      includeAllIndividualChats = false
      includeAllGroupChats = false
      folderType = ChatFolderRecord.FolderType.CUSTOM
      deletedAtTimestampMs = 0L
    }.build()
    val record = SignalChatFolderRecord(STORAGE_ID, proto)

    // WHEN
    val result = testSubject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `Given an invalid proto with a bad id, assert invalid`() {
    // GIVEN
    val proto = ChatFolderRecord.Builder().apply {
      identifier = "bad".toByteArray().toByteString()
      name = "Folder1"
      position = -1
      showOnlyUnread = false
      showMutedChats = false
      includeAllIndividualChats = false
      includeAllGroupChats = false
      folderType = ChatFolderRecord.FolderType.CUSTOM
      deletedAtTimestampMs = 0L
    }.build()
    val record = SignalChatFolderRecord(STORAGE_ID, proto)

    // WHEN
    val result = testSubject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `Given an invalid proto with a bad recipient, assert invalid`() {
    // GIVEN
    val proto = ChatFolderRecord.Builder().apply {
      identifier = UuidUtil.toByteArray(UUID.randomUUID()).toByteString()
      name = "Folder1"
      position = -1
      showOnlyUnread = false
      showMutedChats = false
      includeAllIndividualChats = false
      includeAllGroupChats = false
      folderType = ChatFolderRecord.FolderType.CUSTOM
      deletedAtTimestampMs = 0L
      includedRecipients = listOf(ChatFolderRecord.Recipient(contact = ChatFolderRecord.Recipient.Contact(serviceId = "bad")))
    }.build()
    val record = SignalChatFolderRecord(STORAGE_ID, proto)

    // WHEN
    val result = testSubject.isInvalid(record)

    // THEN
    assertTrue(result)
  }
}
