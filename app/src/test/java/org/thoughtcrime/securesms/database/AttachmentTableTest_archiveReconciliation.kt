/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.app.Application
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import io.mockk.every
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.database.update
import org.signal.core.models.backup.MediaId
import org.signal.core.models.backup.MediaName
import org.signal.core.models.backup.MediaRootBackupKey
import org.signal.core.models.database.AttachmentId
import org.signal.core.util.Base64
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.attachments.ArchivedAttachment
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.backup.v2.ArchivedMediaObject
import org.thoughtcrime.securesms.database.AttachmentTable.ArchiveTransferState
import org.thoughtcrime.securesms.mms.IncomingMessage
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import org.thoughtcrime.securesms.testutil.SystemOutLogger
import java.util.UUID
import kotlin.random.Random

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class AttachmentTableTest_archiveReconciliation {

  @get:Rule val recipients = RecipientTestRule()

  companion object {
    private val MEDIA_ROOT_BACKUP_KEY = MediaRootBackupKey(Random.nextBytes(32))

    @BeforeClass
    @JvmStatic
    fun setUpClass() {
      Log.initialize(SystemOutLogger())
    }
  }

  @Before
  fun setUp() {
    every { recipients.signalStore.backup.mediaRootBackupKey } returns MEDIA_ROOT_BACKUP_KEY
  }

  @Test
  fun `media objects matching a local attachment or its thumbnail are found`() {
    val first = insertAttachment()
    val second = insertAttachment()
    val missing = ArchivedMediaObject(mediaId = MediaId(Random.nextBytes(15)).encode(), cdn = 3)

    val objects = setOf(
      ArchivedMediaObject(mediaId = first.mediaId(), cdn = 3),
      ArchivedMediaObject(mediaId = second.thumbnailMediaId(), cdn = 3),
      missing
    )

    assertThat(SignalDatabase.attachments.getMediaObjectsThatCantBeFound(objects)).containsExactlyInAnyOrder(missing)
  }

  @Test
  fun `media objects only referenced by messages older than the cutoff can't be found`() {
    val old = insertAttachment(receivedTime = 100)
    val recent = insertAttachment(receivedTime = 1_000)

    val oldObject = ArchivedMediaObject(mediaId = old.mediaId(), cdn = 3)
    val oldThumbnailObject = ArchivedMediaObject(mediaId = old.thumbnailMediaId(), cdn = 3)
    val objects = setOf(
      oldObject,
      oldThumbnailObject,
      ArchivedMediaObject(mediaId = recent.mediaId(), cdn = 3),
      ArchivedMediaObject(mediaId = recent.thumbnailMediaId(), cdn = 3)
    )

    assertThat(SignalDatabase.attachments.getMediaObjectsThatCantBeFound(objects, messageInclusionCutoffTime = 500)).containsExactlyInAnyOrder(oldObject, oldThumbnailObject)
  }

  @Test
  fun `a media name shared with a message newer than the cutoff can still be found`() {
    val old = insertAttachment(receivedTime = 100)
    insertAttachment(receivedTime = 1_000, plaintextHash = old.plaintextHash, remoteKey = old.remoteKey)

    val objects = setOf(ArchivedMediaObject(mediaId = old.mediaId(), cdn = 3))

    assertThat(SignalDatabase.attachments.getMediaObjectsThatCantBeFound(objects, messageInclusionCutoffTime = 500)).isEmpty()
  }

  @Test
  fun `media ids map back to the full attachment and message`() {
    val full = insertAttachment()
    val thumbnail = insertAttachment()
    insertAttachment()

    val matches = SignalDatabase.attachments.getAttachmentDataForMediaIds(
      listOf(MediaId(full.mediaId()), MediaId(thumbnail.thumbnailMediaId()), MediaId(Random.nextBytes(15)))
    )

    assertThat(matches).hasSize(2)

    val fullMatch = matches.single { !it.isThumbnail }
    assertThat(fullMatch.attachment.attachmentId).isEqualTo(full.id)
    assertThat(fullMatch.attachment.dataHash).isEqualTo(full.plaintextHash)
    assertThat(fullMatch.mediaId.encode()).isEqualTo(full.mediaId())
    assertThat(fullMatch.messageRecord!!.id).isEqualTo(fullMatch.attachment.mmsId)
    assertThat(fullMatch.isWallpaper).isFalse()

    val thumbnailMatch = matches.single { it.isThumbnail }
    assertThat(thumbnailMatch.attachment.attachmentId).isEqualTo(thumbnail.id)
    assertThat(thumbnailMatch.mediaId.encode()).isEqualTo(thumbnail.thumbnailMediaId())
  }

  @Test
  fun `attachments sharing a media name produce a single match`() {
    val original = insertAttachment()
    insertAttachment(plaintextHash = original.plaintextHash, remoteKey = original.remoteKey)

    val matches = SignalDatabase.attachments.getAttachmentDataForMediaIds(listOf(MediaId(original.mediaId())))

    assertThat(matches.single().attachment.attachmentId).isEqualTo(original.id)
  }

  @Test
  fun `unknown media ids produce no matches`() {
    insertAttachment()

    assertThat(SignalDatabase.attachments.getAttachmentDataForMediaIds(listOf(MediaId(Random.nextBytes(15))))).isEmpty()
  }

  @Test
  fun `matching unfinished attachments are marked finished on the given cdn`() {
    val pending = insertAttachment(state = ArchiveTransferState.NONE)
    val alreadyFinished = insertAttachment(state = ArchiveTransferState.FINISHED)
    val unmatched = insertAttachment(state = ArchiveTransferState.NONE)

    val updated = SignalDatabase.attachments.setArchiveFinishedForMatchingMediaObjects(
      setOf(
        ArchivedMediaObject(mediaId = pending.mediaId(), cdn = 5),
        ArchivedMediaObject(mediaId = alreadyFinished.mediaId(), cdn = 6)
      )
    )

    assertThat(updated).isEqualTo(1)

    val pendingResult = SignalDatabase.attachments.getAttachment(pending.id)!!
    assertThat(pendingResult.archiveTransferState).isEqualTo(ArchiveTransferState.FINISHED)
    assertThat(pendingResult.archiveCdn).isEqualTo(5)

    assertThat(SignalDatabase.attachments.getAttachment(unmatched.id)!!.archiveTransferState).isEqualTo(ArchiveTransferState.NONE)
    assertThat(SignalDatabase.attachments.getAttachment(alreadyFinished.id)!!.archiveCdn).isEqualTo(3)
  }

  @Test
  fun `every row sharing a matched media name is marked finished`() {
    val original = insertAttachment(state = ArchiveTransferState.NONE)
    val duplicate = insertAttachment(state = ArchiveTransferState.TEMPORARY_FAILURE, plaintextHash = original.plaintextHash, remoteKey = original.remoteKey)

    val updated = SignalDatabase.attachments.setArchiveFinishedForMatchingMediaObjects(setOf(ArchivedMediaObject(mediaId = original.mediaId(), cdn = 5)))

    assertThat(updated).isEqualTo(1)
    assertThat(SignalDatabase.attachments.getAttachment(original.id)!!.archiveTransferState).isEqualTo(ArchiveTransferState.FINISHED)
    assertThat(SignalDatabase.attachments.getAttachment(duplicate.id)!!.archiveTransferState).isEqualTo(ArchiveTransferState.FINISHED)
  }

  private fun insertAttachment(
    state: ArchiveTransferState = ArchiveTransferState.NONE,
    plaintextHash: String = Base64.encodeWithPadding(Random.nextBytes(32)),
    remoteKey: String = Base64.encodeWithPadding(Random.nextBytes(64)),
    receivedTime: Long = 200L
  ): InsertedAttachment {
    val attachmentId = insertArchivedAttachment(receivedTime)

    SignalDatabase.attachments.writableDatabase
      .update(AttachmentTable.TABLE_NAME)
      .values(
        AttachmentTable.DATA_HASH_END to plaintextHash,
        AttachmentTable.REMOTE_KEY to remoteKey,
        AttachmentTable.ARCHIVE_TRANSFER_STATE to state.value
      )
      .where("${AttachmentTable.ID} = ?", attachmentId.id)
      .run()

    return InsertedAttachment(attachmentId, plaintextHash, remoteKey)
  }

  private fun insertArchivedAttachment(receivedTime: Long): AttachmentId {
    val from = recipients.createRecipient("Some Contact")
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(from))

    val message = IncomingMessage(
      type = MessageType.NORMAL,
      from = from,
      body = null,
      sentTimeMillis = 100L,
      serverTimeMillis = 100L,
      receivedTimeMillis = receivedTime,
      attachments = listOf(createArchivedAttachment())
    )

    val messageId = SignalDatabase.messages.insertMessageInbox(message, threadId).get().messageId
    return SignalDatabase.attachments.getAttachmentsForMessage(messageId).first().attachmentId
  }

  private fun createArchivedAttachment(): Attachment {
    return ArchivedAttachment(
      contentType = "image/jpeg",
      size = 1024,
      cdn = 3,
      uploadTimestamp = 0,
      key = Random.nextBytes(32),
      cdnKey = "password",
      archiveCdn = 3,
      plaintextHash = Random.nextBytes(32),
      incrementalMac = Random.nextBytes(8),
      incrementalMacChunkSize = 8,
      width = 100,
      height = 100,
      caption = null,
      blurHash = null,
      voiceNote = false,
      borderless = false,
      stickerLocator = null,
      gif = false,
      quote = false,
      quoteTargetContentType = null,
      uuid = UUID.randomUUID(),
      fileName = null,
      localBackupKey = null
    )
  }

  private class InsertedAttachment(val id: AttachmentId, val plaintextHash: String, val remoteKey: String) {
    fun mediaId(): String {
      return MediaName.fromPlaintextHashAndRemoteKey(Base64.decode(plaintextHash), Base64.decode(remoteKey)).toMediaId(MEDIA_ROOT_BACKUP_KEY).encode()
    }

    fun thumbnailMediaId(): String {
      return MediaName.fromPlaintextHashAndRemoteKeyForThumbnail(Base64.decode(plaintextHash), Base64.decode(remoteKey)).toMediaId(MEDIA_ROOT_BACKUP_KEY).encode()
    }
  }
}
