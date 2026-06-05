package org.thoughtcrime.securesms.util

import android.content.Context
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.attachments.Cdn
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.NoSuchMessageException
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.ThreadTable
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.jobmanager.impl.NotInCallConstraint
import org.thoughtcrime.securesms.jobs.MultiDeviceDeleteSyncJob
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.stickers.StickerLocator

class AttachmentUtilTest {

  private val context = mockk<Context>(relaxed = true)
  private val messageTable = mockk<MessageTable>(relaxed = true)
  private val threadTable = mockk<ThreadTable>(relaxed = true)
  private val attachmentTable = mockk<AttachmentTable>(relaxed = true)
  private val fromRecipient = mockk<Recipient>(relaxed = true)
  private val toRecipient = mockk<Recipient>(relaxed = true)
  private val messageRecord = mockk<MessageRecord>(relaxed = true)

  @Before
  fun setUp() {
    mockkObject(SignalDatabase.Companion)
    every { SignalDatabase.instance } returns mockk {
      every { messageTable } returns this@AttachmentUtilTest.messageTable
      every { threadTable } returns this@AttachmentUtilTest.threadTable
      every { attachmentTable } returns this@AttachmentUtilTest.attachmentTable
    }

    mockkStatic(NotInCallConstraint::class)
    mockkStatic(NetworkUtil::class)
    mockkStatic(TextSecurePreferences::class)
    mockkObject(MultiDeviceDeleteSyncJob.Companion)
    every { MultiDeviceDeleteSyncJob.enqueueAttachmentDelete(any(), any()) } just Runs

    every { NotInCallConstraint.isNotInConnectedCall() } returns true
    every { NetworkUtil.isConnectedWifi(context) } returns true
    every { NetworkUtil.isConnectedRoaming(context) } returns false
    every { NetworkUtil.isConnectedMobile(context) } returns false
    every { TextSecurePreferences.getWifiMediaDownloadAllowed(context) } returns emptySet()
    every { TextSecurePreferences.getRoamingMediaDownloadAllowed(context) } returns emptySet()
    every { TextSecurePreferences.getMobileMediaDownloadAllowed(context) } returns emptySet()

    every { messageTable.getMessageRecord(any()) } returns messageRecord
    every { messageRecord.fromRecipient } returns fromRecipient
    every { messageRecord.threadId } returns 1L
    every { messageRecord.isOutgoing } returns false
    every { threadTable.getRecipientForThreadId(any()) } returns toRecipient
    every { toRecipient.isGroup } returns false
    every { fromRecipient.isSystemContact } returns true
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  // --- isAutoDownloadPermitted ---

  @Test
  fun `null attachment returns true`() {
    assertTrue(AttachmentUtil.isAutoDownloadPermitted(context, null))
  }

  @Test
  fun `untrusted conversation returns false`() {
    every { fromRecipient.isSystemContact } returns false
    every { fromRecipient.isProfileSharing } returns false
    every { fromRecipient.isSelf } returns false
    every { fromRecipient.isReleaseNotes } returns false
    every { messageRecord.isOutgoing } returns false

    assertFalse(AttachmentUtil.isAutoDownloadPermitted(context, attachment()))
  }

  @Test
  fun `NoSuchMessageException treats as untrusted`() {
    every { messageTable.getMessageRecord(any()) } throws NoSuchMessageException("")
    assertFalse(AttachmentUtil.isAutoDownloadPermitted(context, attachment()))
  }

  @Test
  fun `group with profile sharing is trusted`() {
    every { toRecipient.isGroup } returns true
    every { toRecipient.isProfileSharing } returns true
    every { fromRecipient.isSystemContact } returns false
    every { TextSecurePreferences.getWifiMediaDownloadAllowed(context) } returns setOf("image")

    assertTrue(AttachmentUtil.isAutoDownloadPermitted(context, attachment()))
  }

  @Test
  fun `group without profile sharing and untrusted individual returns false`() {
    every { toRecipient.isGroup } returns true
    every { toRecipient.isProfileSharing } returns false
    every { fromRecipient.isSystemContact } returns false
    every { fromRecipient.isProfileSharing } returns false
    every { fromRecipient.isSelf } returns false
    every { fromRecipient.isReleaseNotes } returns false
    every { messageRecord.isOutgoing } returns false

    assertFalse(AttachmentUtil.isAutoDownloadPermitted(context, attachment()))
  }

  @Test
  fun `null toRecipient falls to individual trust`() {
    every { threadTable.getRecipientForThreadId(any()) } returns null
    every { TextSecurePreferences.getWifiMediaDownloadAllowed(context) } returns setOf("image")

    assertTrue(AttachmentUtil.isAutoDownloadPermitted(context, attachment()))
  }

  @Test
  fun `profile sharing from recipient is trusted`() {
    every { fromRecipient.isSystemContact } returns false
    every { fromRecipient.isProfileSharing } returns true
    every { TextSecurePreferences.getWifiMediaDownloadAllowed(context) } returns setOf("image")

    assertTrue(AttachmentUtil.isAutoDownloadPermitted(context, attachment()))
  }

  @Test
  fun `video gif blocked when in call`() {
    val attachment = attachment(videoGif = true, contentType = "video/mp4")
    every { TextSecurePreferences.getWifiMediaDownloadAllowed(context) } returns setOf("image")
    every { NotInCallConstraint.isNotInConnectedCall() } returns false

    assertFalse(AttachmentUtil.isAutoDownloadPermitted(context, attachment))
  }

  @Test
  fun `document blocked when in call`() {
    val attachment = attachment(contentType = "application/pdf", fileName = "doc.pdf")
    every { TextSecurePreferences.getWifiMediaDownloadAllowed(context) } returns setOf("documents")
    every { NotInCallConstraint.isNotInConnectedCall() } returns false
    assertFalse(AttachmentUtil.isAutoDownloadPermitted(context, attachment))
  }

  @Test
  fun `group without profile sharing falls to individual trust`() {
    every { toRecipient.isGroup } returns true
    every { toRecipient.isProfileSharing } returns false
    every { fromRecipient.isSystemContact } returns false
    every { fromRecipient.isProfileSharing } returns false
    every { messageRecord.isOutgoing } returns true
    every { TextSecurePreferences.getWifiMediaDownloadAllowed(context) } returns setOf("image")

    assertTrue(AttachmentUtil.isAutoDownloadPermitted(context, attachment()))
  }

  @Test
  fun `outgoing message is trusted`() {
    every { fromRecipient.isSystemContact } returns false
    every { messageRecord.isOutgoing } returns true
    every { TextSecurePreferences.getWifiMediaDownloadAllowed(context) } returns setOf("image")

    assertTrue(AttachmentUtil.isAutoDownloadPermitted(context, attachment()))
  }

  @Test
  fun `self recipient is trusted`() {
    every { fromRecipient.isSystemContact } returns false
    every { fromRecipient.isSelf } returns true
    every { TextSecurePreferences.getWifiMediaDownloadAllowed(context) } returns setOf("image")

    assertTrue(AttachmentUtil.isAutoDownloadPermitted(context, attachment()))
  }

  @Test
  fun `release notes recipient is trusted`() {
    every { fromRecipient.isSystemContact } returns false
    every { fromRecipient.isReleaseNotes } returns true
    every { TextSecurePreferences.getWifiMediaDownloadAllowed(context) } returns setOf("image")

    assertTrue(AttachmentUtil.isAutoDownloadPermitted(context, attachment()))
  }

  @Test
  fun `zero size rejects`() {
    every { TextSecurePreferences.getWifiMediaDownloadAllowed(context) } returns setOf("image")
    assertFalse(AttachmentUtil.isAutoDownloadPermitted(context, attachment(size = 0L, contentType = "image/jpeg")))
  }

  @Test
  fun `ciphertext over 200MB rejects`() {
    val huge = attachment(size = 250L * 1024 * 1024, contentType = "image/jpeg")
    every { TextSecurePreferences.getWifiMediaDownloadAllowed(context) } returns setOf("image")

    assertFalse(AttachmentUtil.isAutoDownloadPermitted(context, huge))
  }

  @Test
  fun `long text within 64 KiB cap permitted`() {
    val attachment = attachment(size = MessageUtil.MAX_TOTAL_BODY_SIZE_BYTES.toLong(), contentType = "text/x-signal-plain")
    assertTrue(AttachmentUtil.isAutoDownloadPermitted(context, attachment))
  }

  @Test
  fun `long text exceeding 64 KiB cap rejected`() {
    val attachment = attachment(size = MessageUtil.MAX_TOTAL_BODY_SIZE_BYTES.toLong() + 1, contentType = "text/x-signal-plain")
    assertFalse(AttachmentUtil.isAutoDownloadPermitted(context, attachment))
  }

  @Test
  fun `small sticker always permitted regardless of allowed types or in-call`() {
    val attachment = attachment(size = 50L * 1024, isSticker = true, contentType = "image/webp")
    every { NotInCallConstraint.isNotInConnectedCall() } returns false

    assertTrue(AttachmentUtil.isAutoDownloadPermitted(context, attachment))
  }

  @Test
  fun `large sticker requires image allowed type`() {
    val attachment = attachment(size = 500L * 1024, isSticker = true, contentType = "image/webp")
    every { TextSecurePreferences.getWifiMediaDownloadAllowed(context) } returns setOf("image")

    assertTrue(AttachmentUtil.isAutoDownloadPermitted(context, attachment))
  }

  @Test
  fun `large sticker blocked when in call`() {
    val attachment = attachment(size = 500L * 1024, isSticker = true, contentType = "image/webp")
    every { TextSecurePreferences.getWifiMediaDownloadAllowed(context) } returns setOf("image")
    every { NotInCallConstraint.isNotInConnectedCall() } returns false

    assertFalse(AttachmentUtil.isAutoDownloadPermitted(context, attachment))
  }

  @Test
  fun `large sticker blocked when image type not allowed`() {
    val attachment = attachment(size = 500L * 1024, isSticker = true, contentType = "image/webp")
    every { TextSecurePreferences.getWifiMediaDownloadAllowed(context) } returns setOf("audio")

    assertFalse(AttachmentUtil.isAutoDownloadPermitted(context, attachment))
  }

  @Test
  fun `small voice note always permitted`() {
    val attachment = attachment(size = 10L * 1024, voiceNote = true, contentType = "audio/aac", fileName = null)
    assertTrue(AttachmentUtil.isAutoDownloadPermitted(context, attachment))
  }

  @Test
  fun `unnamed audio without voice note flag uses audio setting`() {
    val attachment = attachment(size = 10L * 1024, voiceNote = false, contentType = "audio/aac", fileName = null)
    every { TextSecurePreferences.getWifiMediaDownloadAllowed(context) } returns setOf("audio")
    assertTrue(AttachmentUtil.isAutoDownloadPermitted(context, attachment))

    every { TextSecurePreferences.getWifiMediaDownloadAllowed(context) } returns emptySet()
    assertFalse(AttachmentUtil.isAutoDownloadPermitted(context, attachment))
  }

  @Test
  fun `named audio uses audio setting`() {
    val attachment = attachment(size = 10L * 1024, voiceNote = false, contentType = "audio/mpeg", fileName = "song.mp3")
    every { TextSecurePreferences.getWifiMediaDownloadAllowed(context) } returns setOf("audio")
    assertTrue(AttachmentUtil.isAutoDownloadPermitted(context, attachment))

    every { TextSecurePreferences.getWifiMediaDownloadAllowed(context) } returns emptySet()
    assertFalse(AttachmentUtil.isAutoDownloadPermitted(context, attachment))
  }

  @Test
  fun `large voice note requires audio allowed type`() {
    val attachment = attachment(size = 500L * 1024, voiceNote = true, contentType = "audio/aac", fileName = null)
    every { TextSecurePreferences.getWifiMediaDownloadAllowed(context) } returns setOf("audio")

    assertTrue(AttachmentUtil.isAutoDownloadPermitted(context, attachment))
  }

  @Test
  fun `large voice note blocked when audio not allowed`() {
    val attachment = attachment(size = 500L * 1024, voiceNote = true, contentType = "audio/aac", fileName = null)
    every { TextSecurePreferences.getWifiMediaDownloadAllowed(context) } returns setOf("image")

    assertFalse(AttachmentUtil.isAutoDownloadPermitted(context, attachment))
  }

  @Test
  fun `large voice note blocked when in call`() {
    val attachment = attachment(size = 500L * 1024, voiceNote = true, contentType = "audio/aac", fileName = null)
    every { TextSecurePreferences.getWifiMediaDownloadAllowed(context) } returns setOf("audio")
    every { NotInCallConstraint.isNotInConnectedCall() } returns false

    assertFalse(AttachmentUtil.isAutoDownloadPermitted(context, attachment))
  }

  @Test
  fun `video gif requires image allowed type`() {
    val attachment = attachment(videoGif = true, contentType = "video/mp4")
    every { TextSecurePreferences.getWifiMediaDownloadAllowed(context) } returns setOf("image")
    assertTrue(AttachmentUtil.isAutoDownloadPermitted(context, attachment))

    every { TextSecurePreferences.getWifiMediaDownloadAllowed(context) } returns emptySet()
    assertFalse(AttachmentUtil.isAutoDownloadPermitted(context, attachment))
  }

  @Test
  fun `image content type uses image setting`() {
    val attachment = attachment(contentType = "image/jpeg")
    every { TextSecurePreferences.getWifiMediaDownloadAllowed(context) } returns setOf("image")
    assertTrue(AttachmentUtil.isAutoDownloadPermitted(context, attachment))

    every { TextSecurePreferences.getWifiMediaDownloadAllowed(context) } returns emptySet()
    assertFalse(AttachmentUtil.isAutoDownloadPermitted(context, attachment))
  }

  @Test
  fun `video content type uses video setting`() {
    val attachment = attachment(contentType = "video/mp4")
    every { TextSecurePreferences.getWifiMediaDownloadAllowed(context) } returns setOf("video")
    assertTrue(AttachmentUtil.isAutoDownloadPermitted(context, attachment))
  }

  @Test
  fun `document content type uses documents setting`() {
    val attachment = attachment(contentType = "application/pdf", fileName = "doc.pdf")
    every { TextSecurePreferences.getWifiMediaDownloadAllowed(context) } returns setOf("documents")
    assertTrue(AttachmentUtil.isAutoDownloadPermitted(context, attachment))

    every { TextSecurePreferences.getWifiMediaDownloadAllowed(context) } returns emptySet()
    assertFalse(AttachmentUtil.isAutoDownloadPermitted(context, attachment))
  }

  @Test
  fun `null content type falls to documents setting`() {
    val attachment = attachment(contentType = null, fileName = "file")
    every { TextSecurePreferences.getWifiMediaDownloadAllowed(context) } returns setOf("documents")
    assertTrue(AttachmentUtil.isAutoDownloadPermitted(context, attachment))
  }

  @Test
  fun `in-call blocks non-sticker paths`() {
    every { NotInCallConstraint.isNotInConnectedCall() } returns false
    every { TextSecurePreferences.getWifiMediaDownloadAllowed(context) } returns setOf("image", "video", "audio", "documents")

    assertFalse(AttachmentUtil.isAutoDownloadPermitted(context, attachment(contentType = "image/jpeg")))
    assertFalse(AttachmentUtil.isAutoDownloadPermitted(context, attachment(contentType = "application/pdf", fileName = "a.pdf")))
  }

  @Test
  fun `roaming network uses roaming allowed set`() {
    every { NetworkUtil.isConnectedWifi(context) } returns false
    every { NetworkUtil.isConnectedRoaming(context) } returns true
    every { TextSecurePreferences.getRoamingMediaDownloadAllowed(context) } returns setOf("image")

    assertTrue(AttachmentUtil.isAutoDownloadPermitted(context, attachment(contentType = "image/jpeg")))
  }

  @Test
  fun `mobile network uses mobile allowed set`() {
    every { NetworkUtil.isConnectedWifi(context) } returns false
    every { NetworkUtil.isConnectedRoaming(context) } returns false
    every { NetworkUtil.isConnectedMobile(context) } returns true
    every { TextSecurePreferences.getMobileMediaDownloadAllowed(context) } returns setOf("image")

    assertTrue(AttachmentUtil.isAutoDownloadPermitted(context, attachment(contentType = "image/jpeg")))
  }

  @Test
  fun `no network yields empty allowed set`() {
    every { NetworkUtil.isConnectedWifi(context) } returns false
    every { NetworkUtil.isConnectedRoaming(context) } returns false
    every { NetworkUtil.isConnectedMobile(context) } returns false

    assertFalse(AttachmentUtil.isAutoDownloadPermitted(context, attachment(contentType = "image/jpeg")))
  }

  // --- deleteAttachment ---

  @Test
  fun `deleteAttachment with single attachment deletes message and returns its record`() {
    val attachment = attachment()
    every { attachmentTable.getAttachmentsForMessage(42L) } returns listOf(attachment)
    every { messageTable.getMessageRecordOrNull(42L) } returns messageRecord

    val result = AttachmentUtil.deleteAttachment(attachment)

    assertTrue(result === messageRecord)
    verify { messageTable.deleteMessage(42L) }
    verify(exactly = 0) { attachmentTable.deleteAttachment(any()) }
  }

  @Test
  fun `deleteAttachment with multiple attachments deletes only the attachment and returns null`() {
    val attachment = attachment()
    every { attachmentTable.getAttachmentsForMessage(42L) } returns listOf(attachment, attachment)
    every { messageTable.getMessageRecordOrNull(42L) } returns messageRecord

    val result = AttachmentUtil.deleteAttachment(attachment)

    assertTrue(result == null)
    verify { attachmentTable.deleteAttachment(AttachmentId(1L)) }
    verify(exactly = 0) { messageTable.deleteMessage(any()) }
  }

  // --- isRestoreOnOpenPermitted ---

  @Test
  fun `restore null attachment returns true`() {
    assertTrue(AttachmentUtil.isRestoreOnOpenPermitted(context, null))
  }

  @Test
  fun `restore null contentType returns false`() {
    assertFalse(AttachmentUtil.isRestoreOnOpenPermitted(context, attachment(contentType = null, fileName = null)))
  }

  @Test
  fun `restore non-image returns false`() {
    assertFalse(AttachmentUtil.isRestoreOnOpenPermitted(context, attachment(contentType = "video/mp4")))
  }

  @Test
  fun `restore image permitted when allowed and not in call`() {
    every { TextSecurePreferences.getWifiMediaDownloadAllowed(context) } returns setOf("image")
    assertTrue(AttachmentUtil.isRestoreOnOpenPermitted(context, attachment(contentType = "image/jpeg")))
  }

  @Test
  fun `restore image blocked when in call`() {
    every { TextSecurePreferences.getWifiMediaDownloadAllowed(context) } returns setOf("image")
    every { NotInCallConstraint.isNotInConnectedCall() } returns false
    assertFalse(AttachmentUtil.isRestoreOnOpenPermitted(context, attachment(contentType = "image/jpeg")))
  }

  @Test
  fun `restore image blocked when type not allowed`() {
    every { TextSecurePreferences.getWifiMediaDownloadAllowed(context) } returns emptySet()
    assertFalse(AttachmentUtil.isRestoreOnOpenPermitted(context, attachment(contentType = "image/jpeg")))
  }

  private fun attachment(
    size: Long = 1_000L,
    contentType: String? = "image/jpeg",
    isSticker: Boolean = false,
    voiceNote: Boolean = false,
    videoGif: Boolean = false,
    fileName: String? = "photo.jpg"
  ): DatabaseAttachment {
    return DatabaseAttachment(
      attachmentId = AttachmentId(1L),
      mmsId = 42L,
      hasData = false,
      hasThumbnail = false,
      contentType = contentType,
      transferProgress = AttachmentTable.TRANSFER_PROGRESS_PENDING,
      size = size,
      fileName = fileName,
      cdn = Cdn.CDN_3,
      location = null,
      key = null,
      digest = null,
      incrementalDigest = null,
      incrementalMacChunkSize = 0,
      fastPreflightId = null,
      voiceNote = voiceNote,
      borderless = false,
      videoGif = videoGif,
      width = 0,
      height = 0,
      quote = false,
      caption = null,
      stickerLocator = if (isSticker) StickerLocator("pack", "key", 0, null) else null,
      blurHash = null,
      audioHash = null,
      transformProperties = null,
      displayOrder = 0,
      uploadTimestamp = 0,
      dataHash = null,
      archiveCdn = null,
      thumbnailRestoreState = AttachmentTable.ThumbnailRestoreState.NONE,
      archiveTransferState = AttachmentTable.ArchiveTransferState.NONE,
      uuid = null,
      quoteTargetContentType = null,
      metadata = null
    )
  }
}
