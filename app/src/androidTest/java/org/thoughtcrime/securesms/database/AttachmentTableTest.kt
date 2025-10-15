package org.thoughtcrime.securesms.database

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.FlakyTest
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotEqualTo
import assertk.assertions.isTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.Base64
import org.signal.core.util.Base64.decodeBase64OrThrow
import org.signal.core.util.copyTo
import org.signal.core.util.stream.NullOutputStream
import org.thoughtcrime.securesms.attachments.ArchivedAttachment
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.attachments.PointerAttachment
import org.thoughtcrime.securesms.attachments.UriAttachment
import org.thoughtcrime.securesms.mms.IncomingMessage
import org.thoughtcrime.securesms.mms.MediaStream
import org.thoughtcrime.securesms.mms.SentMediaQuality
import org.thoughtcrime.securesms.providers.BlobProvider
import org.thoughtcrime.securesms.testing.SignalActivityRule
import org.thoughtcrime.securesms.util.MediaUtil
import org.whispersystems.signalservice.api.crypto.AttachmentCipherOutputStream
import org.whispersystems.signalservice.api.crypto.NoCipherOutputStream
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentRemoteId
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Optional
import java.util.UUID
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class AttachmentTableTest {

  @get:Rule
  val harness = SignalActivityRule(othersCount = 10)

  @Before
  fun setUp() {
    SignalDatabase.attachments.deleteAllAttachments()
  }

  @Test
  fun givenABlob_whenIInsert2AttachmentsForPreUpload_thenIExpectDistinctIdsButSameFileName() {
    val blob = BlobProvider.getInstance().forData(byteArrayOf(1, 2, 3, 4, 5)).createForSingleSessionInMemory()
    val highQualityProperties = createHighQualityTransformProperties()
    val highQualityImage = createAttachment(1, blob, highQualityProperties)
    val attachment = SignalDatabase.attachments.insertAttachmentForPreUpload(highQualityImage)
    val attachment2 = SignalDatabase.attachments.insertAttachmentForPreUpload(highQualityImage)

    assertNotEquals(attachment2.attachmentId, attachment.attachmentId)
    assertEquals(attachment2.fileName, attachment.fileName)
  }

  @FlakyTest
  @Test
  fun givenABlobAndDifferentTransformQuality_whenIInsert2AttachmentsForPreUpload_thenIExpectDifferentFileInfos() {
    val blob = BlobProvider.getInstance().forData(byteArrayOf(1, 2, 3, 4, 5)).createForSingleSessionInMemory()
    val highQualityProperties = createHighQualityTransformProperties()
    val highQualityImage = createAttachment(1, blob, highQualityProperties)
    val lowQualityImage = createAttachment(1, blob, AttachmentTable.TransformProperties.empty())
    val attachment = SignalDatabase.attachments.insertAttachmentForPreUpload(highQualityImage)
    val attachment2 = SignalDatabase.attachments.insertAttachmentForPreUpload(lowQualityImage)

    SignalDatabase.attachments.updateAttachmentData(
      attachment,
      createMediaStream(byteArrayOf(1, 2, 3, 4, 5))
    )

    SignalDatabase.attachments.updateAttachmentData(
      attachment2,
      createMediaStream(byteArrayOf(1, 2, 3))
    )

    val attachment1Info = SignalDatabase.attachments.getDataFileInfo(attachment.attachmentId)
    val attachment2Info = SignalDatabase.attachments.getDataFileInfo(attachment2.attachmentId)

    assertNotEquals(attachment1Info, attachment2Info)
  }

  @FlakyTest
  @Ignore("test is flaky")
  @Test
  fun givenIdenticalAttachmentsInsertedForPreUpload_whenIUpdateAttachmentDataAndSpecifyOnlyModifyThisAttachment_thenIExpectDifferentFileInfos() {
    val blob = BlobProvider.getInstance().forData(byteArrayOf(1, 2, 3, 4, 5)).createForSingleSessionInMemory()
    val highQualityProperties = createHighQualityTransformProperties()
    val highQualityImage = createAttachment(1, blob, highQualityProperties)
    val attachment = SignalDatabase.attachments.insertAttachmentForPreUpload(highQualityImage)
    val attachment2 = SignalDatabase.attachments.insertAttachmentForPreUpload(highQualityImage)

    SignalDatabase.attachments.updateAttachmentData(
      attachment,
      createMediaStream(byteArrayOf(1, 2, 3, 4, 5))
    )

    SignalDatabase.attachments.updateAttachmentData(
      attachment2,
      createMediaStream(byteArrayOf(1, 2, 3, 4))
    )

    val attachment1Info = SignalDatabase.attachments.getDataFileInfo(attachment.attachmentId)
    val attachment2Info = SignalDatabase.attachments.getDataFileInfo(attachment2.attachmentId)

    assertNotEquals(attachment1Info, attachment2Info)
  }

  /**
   * Given: A previous attachment and two pre-upload attachments with the same data but different transform properties (standard and high).
   *
   * When changing content of standard pre-upload attachment to match pre-existing attachment
   *
   * Then update standard pre-upload attachment to match previous attachment, do not update high pre-upload attachment, and do
   * not delete shared pre-upload uri from disk as it is still being used by the high pre-upload attachment.
   */
  @Test
  fun doNotDeleteDedupedFileIfUsedByAnotherAttachmentWithADifferentTransformProperties() {
    // GIVEN
    val uncompressData = byteArrayOf(1, 2, 3, 4, 5)
    val compressedData = byteArrayOf(1, 2, 3)

    val blobUncompressed = BlobProvider.getInstance().forData(uncompressData).createForSingleSessionInMemory()

    val previousAttachment = createAttachment(1, BlobProvider.getInstance().forData(compressedData).createForSingleSessionInMemory(), AttachmentTable.TransformProperties.empty())
    val previousDatabaseAttachmentId: AttachmentId = SignalDatabase.attachments.insertAttachmentsForMessage(1, listOf(previousAttachment), emptyList()).values.first()

    val standardQualityPreUpload = createAttachment(1, blobUncompressed, AttachmentTable.TransformProperties.empty())
    val standardDatabaseAttachment = SignalDatabase.attachments.insertAttachmentForPreUpload(standardQualityPreUpload)

    val highQualityPreUpload = createAttachment(1, blobUncompressed, AttachmentTable.TransformProperties.forSentMediaQuality(Optional.empty(), SentMediaQuality.HIGH))
    val highDatabaseAttachment = SignalDatabase.attachments.insertAttachmentForPreUpload(highQualityPreUpload)

    // WHEN
    SignalDatabase.attachments.updateAttachmentData(standardDatabaseAttachment, createMediaStream(compressedData))

    // THEN
    val previousInfo = SignalDatabase.attachments.getDataFileInfo(previousDatabaseAttachmentId)!!
    val standardInfo = SignalDatabase.attachments.getDataFileInfo(standardDatabaseAttachment.attachmentId)!!
    val highInfo = SignalDatabase.attachments.getDataFileInfo(highDatabaseAttachment.attachmentId)!!

    assertNotEquals(standardInfo, highInfo)
    assertThat(highInfo.file).isNotEqualTo(standardInfo.file)
    assertThat(highInfo.file.exists()).isEqualTo(true)
  }

  /**
   * Given: Three pre-upload attachments with the same data but different transform properties (1x standard and 2x high).
   *
   * When inserting content of high pre-upload attachment.
   *
   * Then do not deduplicate with standard pre-upload attachment, but do deduplicate second high insert.
   */
  @Test
  fun doNotDedupedFileIfUsedByAnotherAttachmentWithADifferentTransformProperties() {
    // GIVEN
    val uncompressData = byteArrayOf(1, 2, 3, 4, 5)
    val blobUncompressed = BlobProvider.getInstance().forData(uncompressData).createForSingleSessionInMemory()

    val standardQualityPreUpload = createAttachment(1, blobUncompressed, AttachmentTable.TransformProperties.empty())
    val standardDatabaseAttachment = SignalDatabase.attachments.insertAttachmentForPreUpload(standardQualityPreUpload)

    // WHEN
    val highQualityPreUpload = createAttachment(1, blobUncompressed, AttachmentTable.TransformProperties.forSentMediaQuality(Optional.empty(), SentMediaQuality.HIGH))
    val highDatabaseAttachment = SignalDatabase.attachments.insertAttachmentForPreUpload(highQualityPreUpload)

    val secondHighQualityPreUpload = createAttachment(1, blobUncompressed, AttachmentTable.TransformProperties.forSentMediaQuality(Optional.empty(), SentMediaQuality.HIGH))
    val secondHighDatabaseAttachment = SignalDatabase.attachments.insertAttachmentForPreUpload(secondHighQualityPreUpload)

    // THEN
    val standardInfo = SignalDatabase.attachments.getDataFileInfo(standardDatabaseAttachment.attachmentId)!!
    val highInfo = SignalDatabase.attachments.getDataFileInfo(highDatabaseAttachment.attachmentId)!!
    val secondHighInfo = SignalDatabase.attachments.getDataFileInfo(secondHighDatabaseAttachment.attachmentId)!!

    assertThat(highInfo.file).isNotEqualTo(standardInfo.file)
    assertThat(secondHighInfo.file).isEqualTo(highInfo.file)
    assertThat(standardInfo.file.exists()).isEqualTo(true)
    assertThat(highInfo.file.exists()).isEqualTo(true)
  }

  @Test
  fun resetArchiveTransferStateByPlaintextHashAndRemoteKey_singleMatch() {
    // Given an attachment with some plaintextHash+remoteKey
    val blob = BlobProvider.getInstance().forData(byteArrayOf(1, 2, 3, 4, 5)).createForSingleSessionInMemory()
    val attachment = createAttachment(1, blob, AttachmentTable.TransformProperties.empty())
    val attachmentId = SignalDatabase.attachments.insertAttachmentsForMessage(-1L, listOf(attachment), emptyList()).values.first()
    SignalDatabase.attachments.finalizeAttachmentAfterUpload(attachmentId, AttachmentTableTestUtil.createUploadResult(attachmentId))
    SignalDatabase.attachments.setArchiveTransferState(attachmentId, AttachmentTable.ArchiveTransferState.FINISHED)

    // Reset the transfer state by plaintextHash+remoteKey
    val plaintextHash = SignalDatabase.attachments.getAttachment(attachmentId)!!.dataHash!!.decodeBase64OrThrow()
    val remoteKey = SignalDatabase.attachments.getAttachment(attachmentId)!!.remoteKey!!.decodeBase64OrThrow()
    SignalDatabase.attachments.resetArchiveTransferStateByPlaintextHashAndRemoteKeyIfNecessary(plaintextHash, remoteKey)

    // Verify it's been reset
    assertThat(SignalDatabase.attachments.getAttachment(attachmentId)!!.archiveTransferState).isEqualTo(AttachmentTable.ArchiveTransferState.NONE)
  }

  @Test
  fun given10NewerAnd10OlderAttachments_whenIGetEachBatch_thenIExpectProperBucketing() {
    val now = System.currentTimeMillis().milliseconds
    val attachments = (0 until 20).map {
      createArchivedAttachment()
    }

    val newMessages = attachments.take(10).mapIndexed { index, attachment ->
      createIncomingMessage(serverTime = now - index.seconds, attachment = attachment)
    }

    val twoMonthsAgo = now - 60.days
    val oldMessages = attachments.drop(10).mapIndexed { index, attachment ->
      createIncomingMessage(serverTime = twoMonthsAgo - index.seconds, attachment = attachment)
    }

    (newMessages + oldMessages).forEach {
      SignalDatabase.messages.insertMessageInbox(it)
    }

    val firstAttachmentsToDownload = SignalDatabase.attachments.getLast30DaysOfRestorableAttachments(500)
    val nextAttachmentsToDownload = SignalDatabase.attachments.getOlderRestorableAttachments(500)

    assertThat(firstAttachmentsToDownload).hasSize(10)
    val resultNewMessages = SignalDatabase.messages.getMessages(firstAttachmentsToDownload.map { it.mmsId })
    resultNewMessages.forEach {
      assertThat(it.serverTimestamp.milliseconds >= now - 30.days).isTrue()
    }

    assertThat(nextAttachmentsToDownload).hasSize(10)
    val resultOldMessages = SignalDatabase.messages.getMessages(nextAttachmentsToDownload.map { it.mmsId })
    resultOldMessages.forEach {
      assertThat(it.serverTimestamp.milliseconds < now - 30.days).isTrue()
    }
  }

  @Test
  fun givenAnAttachmentWithAMessageThatExpiresIn5Minutes_whenIGetAttachmentsThatNeedArchiveUpload_thenIDoNotExpectThatAttachment() {
    // GIVEN
    val uncompressData = byteArrayOf(1, 2, 3, 4, 5)
    val blobUncompressed = BlobProvider.getInstance().forData(uncompressData).createForSingleSessionInMemory()
    val attachment = createAttachment(1, blobUncompressed, AttachmentTable.TransformProperties.empty())
    val message = createIncomingMessage(serverTime = 0.days, attachment = attachment, expiresIn = 5.minutes)
    val messageId = SignalDatabase.messages.insertMessageInbox(message).map { it.messageId }.get()
    SignalDatabase.attachments.setArchiveTransferState(AttachmentId(1L), AttachmentTable.ArchiveTransferState.NONE)
    SignalDatabase.attachments.setTransferState(messageId, AttachmentId(1L), AttachmentTable.TRANSFER_PROGRESS_DONE)
    SignalDatabase.attachments.finalizeAttachmentAfterUpload(AttachmentId(1L), AttachmentTableTestUtil.createUploadResult(AttachmentId(1L)))

    // WHEN
    val attachments = SignalDatabase.attachments.getAttachmentsThatNeedArchiveUpload()

    // THEN
    assertThat(attachments).isEmpty()
  }

  @Test
  fun givenAnAttachmentWithAMessageThatExpiresIn5Days_whenIGetAttachmentsThatNeedArchiveUpload_thenIDoExpectThatAttachment() {
    // GIVEN
    val uncompressData = byteArrayOf(1, 2, 3, 4, 5)
    val blobUncompressed = BlobProvider.getInstance().forData(uncompressData).createForSingleSessionInMemory()
    val attachment = createAttachment(1, blobUncompressed, AttachmentTable.TransformProperties.empty())
    val message = createIncomingMessage(serverTime = 0.days, attachment = attachment, expiresIn = 5.days)
    val messageId = SignalDatabase.messages.insertMessageInbox(message).map { it.messageId }.get()
    SignalDatabase.attachments.setArchiveTransferState(AttachmentId(1L), AttachmentTable.ArchiveTransferState.NONE)
    SignalDatabase.attachments.setTransferState(messageId, AttachmentId(1L), AttachmentTable.TRANSFER_PROGRESS_DONE)
    SignalDatabase.attachments.finalizeAttachmentAfterUpload(AttachmentId(1L), AttachmentTableTestUtil.createUploadResult(AttachmentId(1L)))

    // WHEN
    val attachments = SignalDatabase.attachments.getAttachmentsThatNeedArchiveUpload()

    // THEN
    assertThat(attachments).isNotEmpty()
  }

  @Test
  fun givenAnAttachmentWithAMessageWithExpirationStartedThatExpiresIn5Days_whenIGetAttachmentsThatNeedArchiveUpload_thenIDoExpectThatAttachment() {
    // GIVEN
    val uncompressData = byteArrayOf(1, 2, 3, 4, 5)
    val blobUncompressed = BlobProvider.getInstance().forData(uncompressData).createForSingleSessionInMemory()
    val attachment = createAttachment(1, blobUncompressed, AttachmentTable.TransformProperties.empty())
    val message = createIncomingMessage(serverTime = 0.days, attachment = attachment, expiresIn = 5.days)
    val messageId = SignalDatabase.messages.insertMessageInbox(message).map { it.messageId }.get()
    SignalDatabase.messages.markExpireStarted(messageId)
    SignalDatabase.attachments.setArchiveTransferState(AttachmentId(1L), AttachmentTable.ArchiveTransferState.NONE)
    SignalDatabase.attachments.setTransferState(messageId, AttachmentId(1L), AttachmentTable.TRANSFER_PROGRESS_DONE)
    SignalDatabase.attachments.finalizeAttachmentAfterUpload(AttachmentId(1L), AttachmentTableTestUtil.createUploadResult(AttachmentId(1L)))

    // WHEN
    val attachments = SignalDatabase.attachments.getAttachmentsThatNeedArchiveUpload()

    // THEN
    assertThat(attachments).isNotEmpty()
  }

  @Test
  fun givenAnAttachmentWithALongTextAttachment_whenIGetAttachmentsThatNeedArchiveUpload_thenIDoNotExpectThatAttachment() {
    // GIVEN
    val uncompressData = byteArrayOf(1, 2, 3, 4, 5)
    val blobUncompressed = BlobProvider.getInstance().forData(uncompressData).createForSingleSessionInMemory()
    val attachment = createAttachment(1, blobUncompressed, AttachmentTable.TransformProperties.empty(), contentType = MediaUtil.LONG_TEXT)
    val message = createIncomingMessage(serverTime = 0.days, attachment = attachment)
    val messageId = SignalDatabase.messages.insertMessageInbox(message).map { it.messageId }.get()
    SignalDatabase.attachments.setArchiveTransferState(AttachmentId(1L), AttachmentTable.ArchiveTransferState.NONE)
    SignalDatabase.attachments.setTransferState(messageId, AttachmentId(1L), AttachmentTable.TRANSFER_PROGRESS_DONE)
    SignalDatabase.attachments.finalizeAttachmentAfterUpload(AttachmentId(1L), AttachmentTableTestUtil.createUploadResult(AttachmentId(1L)))

    // WHEN
    val attachments = SignalDatabase.attachments.getAttachmentsThatNeedArchiveUpload()

    // THEN
    assertThat(attachments).isEmpty()
  }

  /**
   * There's a race condition where the following was happening:
   *
   * 1. Receive attachment A
   * 2. Download attachment A
   * 3. Enqueue copy to archive job for A (old media name)
   * 4. Receive attachment B that is identical to A
   * 5. Dedupe B with A's data file but update A to match B's "newer" remote key
   * 6. Enqueue copy to archive job for B (new media name)
   * 7. Copy to archive for A succeeds for old media name, updating A and B to FINISHED
   * 8. Copy to archive for B for new media name early aborts because B is already marked FINISHED
   *
   * THe problem is Step 7 because it's marking attachments as archived but under the old media and not the new media name.
   *
   * This tests recreates the flow but ensures Step 7 doesn't mark A and B as finished so that Step 8 will not early abort and copy
   * B over with the new media name.
   */
  @Test
  fun givenAnDuplicateAttachmentPriorToCopyToArchive_whenICopyFirstAttachmentToArchive_thenIDoNotExpectBothAttachmentsToChangeArchiveStateToFinished() {
    val data = byteArrayOf(1, 2, 3, 4, 5)

    val attachment1 = createAttachmentPointer("remote-key-1".toByteArray(), data.size)
    val attachment2 = createAttachmentPointer("remote-key-2".toByteArray(), data.size)

    // Insert Message 1
    val message1Result = SignalDatabase.messages.insertMessageInbox(createIncomingMessage(serverTime = 0.days, attachment = attachment1)).get()
    val message1Id = message1Result.messageId
    val attachment1Id = message1Result.insertedAttachments!![attachment1]!!
    // AttachmentDownloadJob#onAdded
    SignalDatabase.attachments.setTransferState(message1Id, attachment1Id, AttachmentTable.TRANSFER_PROGRESS_STARTED)

    // Insert Message 2
    val message2Result = SignalDatabase.messages.insertMessageInbox(createIncomingMessage(serverTime = 1.days, attachment = attachment2)).get()
    val message2Id = message2Result.messageId
    val attachment2Id = message2Result.insertedAttachments!![attachment2]!!
    // AttachmentDownloadJob#onAdded
    SignalDatabase.attachments.setTransferState(message2Id, attachment2Id, AttachmentTable.TRANSFER_PROGRESS_STARTED)

    // Finalize Attachment 1 download
    SignalDatabase.attachments.finalizeAttachmentAfterDownload(message1Id, attachment1Id, ByteArrayInputStream(data))
    // CopyAttachmentToArchiveJob#onAdded
    SignalDatabase.attachments.setArchiveTransferState(attachment1Id, AttachmentTable.ArchiveTransferState.COPY_PENDING)

    // Verify Attachment 1 data matches original Attachment 1 data from insert
    var dbAttachment1 = SignalDatabase.attachments.getAttachment(attachment1Id)!!
    assertThat(dbAttachment1.archiveTransferState).isEqualTo(AttachmentTable.ArchiveTransferState.COPY_PENDING)
    assertThat(dbAttachment1.remoteKey).isEqualTo(Base64.encodeWithPadding("remote-key-1".toByteArray()))

    val attachment1InitialRemoteKey = dbAttachment1.remoteKey!!
    val attachment1InitialPlaintextHash = dbAttachment1.dataHash!!

    // Finalize Attachment 2
    SignalDatabase.attachments.finalizeAttachmentAfterDownload(message2Id, attachment2Id, ByteArrayInputStream(data))

    // Verify Attachment 1 data matches Attachment 2 data from insert and dedupe in finalize
    dbAttachment1 = SignalDatabase.attachments.getAttachment(attachment1Id)!!
    var dbAttachment2 = SignalDatabase.attachments.getAttachment(attachment2Id)!!
    assertThat(dbAttachment1.archiveTransferState).isEqualTo(AttachmentTable.ArchiveTransferState.NONE)
    assertThat(dbAttachment2.archiveTransferState).isEqualTo(AttachmentTable.ArchiveTransferState.NONE)
    assertThat(dbAttachment1.remoteKey).isEqualTo(dbAttachment2.remoteKey)
    assertThat(dbAttachment1.dataHash).isEqualTo(dbAttachment2.dataHash)

    val attachment2InitialRemoteKey = dbAttachment2.remoteKey!!
    val attachment2InitialPlaintextHash = dbAttachment2.dataHash!!

    // "Finish" Copy to Archive for Attachment 1
    SignalDatabase.attachments.setArchiveTransferState(attachment1Id, attachment1InitialRemoteKey, attachment1InitialPlaintextHash, AttachmentTable.ArchiveTransferState.FINISHED)

    dbAttachment1 = SignalDatabase.attachments.getAttachment(attachment1Id)!!
    dbAttachment2 = SignalDatabase.attachments.getAttachment(attachment2Id)!!

    // Verify Attachment 1 and 2 are not updated as FINISHED since Attachment 1's media name parts have changed
    assertThat(dbAttachment1.archiveTransferState).isEqualTo(AttachmentTable.ArchiveTransferState.NONE)
    assertThat(dbAttachment2.archiveTransferState).isEqualTo(AttachmentTable.ArchiveTransferState.NONE)

    // "Finish" Copy to Archive for Attachment 2
    SignalDatabase.attachments.setArchiveTransferState(attachment2Id, attachment2InitialRemoteKey, attachment2InitialPlaintextHash, AttachmentTable.ArchiveTransferState.FINISHED)

    dbAttachment1 = SignalDatabase.attachments.getAttachment(attachment1Id)!!
    dbAttachment2 = SignalDatabase.attachments.getAttachment(attachment2Id)!!

    // Verify Attachment 1 and 2 are updated as FINISHED
    assertThat(dbAttachment1.archiveTransferState).isEqualTo(AttachmentTable.ArchiveTransferState.FINISHED)
    assertThat(dbAttachment2.archiveTransferState).isEqualTo(AttachmentTable.ArchiveTransferState.FINISHED)
  }

  private fun createIncomingMessage(
    serverTime: Duration,
    attachment: Attachment,
    expiresIn: Duration = Duration.ZERO
  ): IncomingMessage {
    return IncomingMessage(
      type = MessageType.NORMAL,
      from = harness.others[0],
      body = null,
      expiresIn = expiresIn.inWholeMilliseconds,
      sentTimeMillis = serverTime.inWholeMilliseconds,
      serverTimeMillis = serverTime.inWholeMilliseconds,
      receivedTimeMillis = serverTime.inWholeMilliseconds,
      attachments = listOf(attachment)
    )
  }

  private fun createAttachmentPointer(key: ByteArray, size: Int): Attachment {
    return PointerAttachment.forPointer(
      pointer = Optional.of(
        SignalServiceAttachmentPointer(
          cdnNumber = 3,
          remoteId = SignalServiceAttachmentRemoteId.V4("asdf"),
          contentType = MediaUtil.IMAGE_JPEG,
          key = key,
          size = Optional.of(size),
          preview = Optional.empty(),
          width = 2,
          height = 2,
          digest = Optional.of(byteArrayOf()),
          incrementalDigest = Optional.empty(),
          incrementalMacChunkSize = 0,
          fileName = Optional.of("file.jpg"),
          voiceNote = false,
          isBorderless = false,
          isGif = false,
          caption = Optional.empty(),
          blurHash = Optional.empty(),
          uploadTimestamp = 0,
          uuid = null
        )
      )
    ).get()
  }

  private fun createArchivedAttachment(): Attachment {
    return ArchivedAttachment(
      contentType = "image/jpeg",
      size = 1024,
      cdn = 3,
      uploadTimestamp = 0,
      key = Random.nextBytes(8),
      cdnKey = "password",
      archiveCdn = 3,
      plaintextHash = Random.nextBytes(8),
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
      fileName = null
    )
  }

  private fun createAttachment(id: Long, uri: Uri, transformProperties: AttachmentTable.TransformProperties, contentType: String = MediaUtil.IMAGE_JPEG): UriAttachment {
    return UriAttachmentBuilder.build(
      id,
      uri = uri,
      contentType = contentType,
      transformProperties = transformProperties
    )
  }

  private fun createHighQualityTransformProperties(): AttachmentTable.TransformProperties {
    return AttachmentTable.TransformProperties.forSentMediaQuality(Optional.empty(), SentMediaQuality.HIGH)
  }

  private fun createMediaStream(byteArray: ByteArray): MediaStream {
    return MediaStream(byteArray.inputStream(), MediaUtil.IMAGE_JPEG, 2, 2)
  }

  private fun getDigest(ciphertext: ByteArray): ByteArray {
    val digestStream = NoCipherOutputStream(NullOutputStream)
    ciphertext.inputStream().copyTo(digestStream)
    return digestStream.transmittedDigest
  }

  private fun encryptPrePaddedBytes(plaintext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
    val outputStream = ByteArrayOutputStream()
    val cipherStream = AttachmentCipherOutputStream(key, iv, outputStream)
    plaintext.inputStream().copyTo(cipherStream)

    return outputStream.toByteArray()
  }

  private fun getTempFile(): File {
    val dir = InstrumentationRegistry.getInstrumentation().targetContext.getDir("temp", Context.MODE_PRIVATE)
    dir.mkdir()
    return File.createTempFile("transfer", ".mms", dir)
  }
}
