package org.thoughtcrime.securesms.database

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.FlakyTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.copyTo
import org.signal.core.util.readFully
import org.signal.core.util.stream.NullOutputStream
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.attachments.PointerAttachment
import org.thoughtcrime.securesms.attachments.UriAttachment
import org.thoughtcrime.securesms.mms.MediaStream
import org.thoughtcrime.securesms.mms.SentMediaQuality
import org.thoughtcrime.securesms.providers.BlobProvider
import org.thoughtcrime.securesms.testing.assertIs
import org.thoughtcrime.securesms.testing.assertIsNot
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.signalservice.api.crypto.AttachmentCipherInputStream
import org.whispersystems.signalservice.api.crypto.AttachmentCipherOutputStream
import org.whispersystems.signalservice.api.crypto.NoCipherOutputStream
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentRemoteId
import org.whispersystems.signalservice.internal.crypto.PaddingInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Optional

@RunWith(AndroidJUnit4::class)
class AttachmentTableTest {

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
    highInfo.file assertIsNot standardInfo.file
    highInfo.file.exists() assertIs true
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

    highInfo.file assertIsNot standardInfo.file
    secondHighInfo.file assertIs highInfo.file
    standardInfo.file.exists() assertIs true
    highInfo.file.exists() assertIs true
  }

  @Test
  fun finalizeAttachmentAfterDownload_fixDigestOnNonZeroPadding() {
    // Insert attachment metadata for badly-padded attachment
    val plaintext = byteArrayOf(1, 2, 3, 4)
    val key = Util.getSecretBytes(64)
    val iv = Util.getSecretBytes(16)

    val badlyPaddedPlaintext = PaddingInputStream(plaintext.inputStream(), plaintext.size.toLong()).readFully().also { it[it.size - 1] = 0x42 }
    val badlyPaddedCiphertext = encryptPrePaddedBytes(badlyPaddedPlaintext, key, iv)
    val badlyPaddedDigest = getDigest(badlyPaddedCiphertext)

    val cipherFile = getTempFile()
    cipherFile.writeBytes(badlyPaddedCiphertext)

    val mmsId = -1L
    val attachmentId = SignalDatabase.attachments.insertAttachmentsForMessage(mmsId, listOf(createAttachmentPointer(key, badlyPaddedDigest, plaintext.size)), emptyList()).values.first()

    // Give data to attachment table
    val cipherInputStream = AttachmentCipherInputStream.createForAttachment(cipherFile, plaintext.size.toLong(), key, badlyPaddedDigest, null, 4, false)
    SignalDatabase.attachments.finalizeAttachmentAfterDownload(mmsId, attachmentId, cipherInputStream, iv)

    // Verify the digest has been updated to the properly padded one
    val properlyPaddedPlaintext = PaddingInputStream(plaintext.inputStream(), plaintext.size.toLong()).readFully()
    val properlyPaddedCiphertext = encryptPrePaddedBytes(properlyPaddedPlaintext, key, iv)
    val properlyPaddedDigest = getDigest(properlyPaddedCiphertext)

    val newDigest = SignalDatabase.attachments.getAttachment(attachmentId)!!.remoteDigest!!

    assertArrayEquals(properlyPaddedDigest, newDigest)
  }

  @Test
  fun finalizeAttachmentAfterDownload_leaveDigestAloneForAllZeroPadding() {
    // Insert attachment metadata for properly-padded attachment
    val plaintext = byteArrayOf(1, 2, 3, 4)
    val key = Util.getSecretBytes(64)
    val iv = Util.getSecretBytes(16)

    val paddedPlaintext = PaddingInputStream(plaintext.inputStream(), plaintext.size.toLong()).readFully()
    val ciphertext = encryptPrePaddedBytes(paddedPlaintext, key, iv)
    val digest = getDigest(ciphertext)

    val cipherFile = getTempFile()
    cipherFile.writeBytes(ciphertext)

    val mmsId = -1L
    val attachmentId = SignalDatabase.attachments.insertAttachmentsForMessage(mmsId, listOf(createAttachmentPointer(key, digest, plaintext.size)), emptyList()).values.first()

    // Give data to attachment table
    val cipherInputStream = AttachmentCipherInputStream.createForAttachment(cipherFile, plaintext.size.toLong(), key, digest, null, 4, false)
    SignalDatabase.attachments.finalizeAttachmentAfterDownload(mmsId, attachmentId, cipherInputStream, iv)

    // Verify the digest hasn't changed
    val newDigest = SignalDatabase.attachments.getAttachment(attachmentId)!!.remoteDigest!!
    assertArrayEquals(digest, newDigest)
  }

  private fun createAttachmentPointer(key: ByteArray, digest: ByteArray, size: Int): Attachment {
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
          digest = Optional.of(digest),
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

  private fun createAttachment(id: Long, uri: Uri, transformProperties: AttachmentTable.TransformProperties): UriAttachment {
    return UriAttachmentBuilder.build(
      id,
      uri = uri,
      contentType = MediaUtil.IMAGE_JPEG,
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
