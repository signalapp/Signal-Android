package org.thoughtcrime.securesms.database

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.FlakyTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.thoughtcrime.securesms.attachments.UriAttachment
import org.thoughtcrime.securesms.mms.MediaStream
import org.thoughtcrime.securesms.mms.SentMediaQuality
import org.thoughtcrime.securesms.providers.BlobProvider
import org.thoughtcrime.securesms.util.MediaUtil
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
      createMediaStream(byteArrayOf(1, 2, 3, 4, 5)),
      false
    )

    SignalDatabase.attachments.updateAttachmentData(
      attachment2,
      createMediaStream(byteArrayOf(1, 2, 3)),
      false
    )

    val attachment1Info = SignalDatabase.attachments.getAttachmentDataFileInfo(attachment.attachmentId, AttachmentTable.DATA)
    val attachment2Info = SignalDatabase.attachments.getAttachmentDataFileInfo(attachment2.attachmentId, AttachmentTable.DATA)

    assertNotEquals(attachment1Info, attachment2Info)
  }

  @FlakyTest
  @Test
  fun givenIdenticalAttachmentsInsertedForPreUpload_whenIUpdateAttachmentDataAndSpecifyOnlyModifyThisAttachment_thenIExpectDifferentFileInfos() {
    val blob = BlobProvider.getInstance().forData(byteArrayOf(1, 2, 3, 4, 5)).createForSingleSessionInMemory()
    val highQualityProperties = createHighQualityTransformProperties()
    val highQualityImage = createAttachment(1, blob, highQualityProperties)
    val attachment = SignalDatabase.attachments.insertAttachmentForPreUpload(highQualityImage)
    val attachment2 = SignalDatabase.attachments.insertAttachmentForPreUpload(highQualityImage)

    SignalDatabase.attachments.updateAttachmentData(
      attachment,
      createMediaStream(byteArrayOf(1, 2, 3, 4, 5)),
      true
    )

    SignalDatabase.attachments.updateAttachmentData(
      attachment2,
      createMediaStream(byteArrayOf(1, 2, 3, 4)),
      true
    )

    val attachment1Info = SignalDatabase.attachments.getAttachmentDataFileInfo(attachment.attachmentId, AttachmentTable.DATA)
    val attachment2Info = SignalDatabase.attachments.getAttachmentDataFileInfo(attachment2.attachmentId, AttachmentTable.DATA)

    assertNotEquals(attachment1Info, attachment2Info)
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
}
