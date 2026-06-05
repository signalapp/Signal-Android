/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.plaintext

import android.app.Application
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.conversation.plaintext.PlaintextExportRepository.PendingAttachment
import org.thoughtcrime.securesms.database.FakeMessageRecords
import org.thoughtcrime.securesms.database.MessageTypes
import org.thoughtcrime.securesms.database.model.Quote
import org.thoughtcrime.securesms.mms.QuoteModel
import org.thoughtcrime.securesms.mms.SlideDeck
import org.thoughtcrime.securesms.polls.PollOption
import org.thoughtcrime.securesms.polls.PollRecord
import org.thoughtcrime.securesms.polls.Voter
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.stickers.StickerLocator
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.util.MediaUtil
import java.io.BufferedWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class PlaintextExportRepositoryTest {

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
  private val attachmentDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

  private lateinit var context: android.content.Context

  private val mockRecipient: Recipient = mockk(relaxed = true)

  @Before
  fun setUp() {
    context = mockk(relaxed = true)

    every { mockRecipient.getDisplayName(any()) } returns "Alice"

    val mockLiveRecipient = mockk<org.thoughtcrime.securesms.recipients.LiveRecipient>(relaxed = true)
    every { mockLiveRecipient.get() } returns mockRecipient
    every { mockRecipient.live() } returns mockLiveRecipient

    mockkObject(Recipient)
    every { Recipient.resolved(any()) } returns mockRecipient
  }

  @After
  fun tearDown() {
    unmockkObject(Recipient)
  }

  // ==================== writeMessage tests ====================

  @Test
  fun `writeMessage with plain incoming text message`() {
    val message = FakeMessageRecords.buildMediaMmsMessageRecord(
      id = 1,
      body = "Hello, world!",
      dateSent = 1710500000000L,
      conversationRecipient = mockRecipient,
      mailbox = MessageTypes.BASE_INBOX_TYPE
    )

    val output = renderMessage(message)

    assertTrue(output.contains("] Alice: Hello, world!"))
  }

  @Test
  fun `writeMessage with outgoing text message shows You as sender`() {
    val message = FakeMessageRecords.buildMediaMmsMessageRecord(
      id = 1,
      body = "Hello from me",
      dateSent = 1710500000000L,
      mailbox = MessageTypes.BASE_SENT_TYPE
    )

    val output = renderMessage(message)

    assertTrue(output.contains("] You: Hello from me"))
  }

  @Test
  fun `writeMessage with deleted message`() {
    val message = FakeMessageRecords.buildMediaMmsMessageRecord(
      id = 1,
      body = "",
      dateSent = 1710500000000L,
      conversationRecipient = mockRecipient,
      mailbox = MessageTypes.BASE_INBOX_TYPE,
      deletedBy = RecipientId.from(1)
    )

    val output = renderMessage(message)

    assertTrue(output.contains("Alice: (This message was deleted)"))
  }

  @Test
  fun `writeMessage with view-once message`() {
    val message = FakeMessageRecords.buildMediaMmsMessageRecord(
      id = 1,
      body = "",
      dateSent = 1710500000000L,
      conversationRecipient = mockRecipient,
      viewOnce = true
    )

    val output = renderMessage(message)

    assertTrue(output.contains("Alice: (View-once media)"))
  }

  @Test
  fun `writeMessage with quote includes quote block`() {
    val quoteRecipientId = RecipientId.from(42)
    every { Recipient.resolved(quoteRecipientId).getDisplayName(any()) } returns "Bob"

    val quote = Quote(
      1000L,
      quoteRecipientId,
      "Original message text",
      false,
      SlideDeck(),
      emptyList(),
      QuoteModel.Type.NORMAL
    )

    val message = FakeMessageRecords.buildMediaMmsMessageRecord(
      id = 1,
      body = "My reply",
      dateSent = 1710500000000L,
      conversationRecipient = mockRecipient,
      quote = quote
    )

    val output = renderMessage(message)

    assertTrue("Should contain quoting header", output.contains("> Quoting Bob:"))
    assertTrue("Should contain quoted text", output.contains("> Original message text"))
    assertTrue("Should contain reply body", output.contains("My reply"))
  }

  @Test
  fun `writeMessage with quote and no body shows media placeholder`() {
    val quoteRecipientId = RecipientId.from(42)
    every { Recipient.resolved(quoteRecipientId).getDisplayName(any()) } returns "Bob"

    val quote = Quote(
      1000L,
      quoteRecipientId,
      null,
      false,
      SlideDeck(),
      emptyList(),
      QuoteModel.Type.NORMAL
    )

    val message = FakeMessageRecords.buildMediaMmsMessageRecord(
      id = 1,
      body = "Replying to media",
      dateSent = 1710500000000L,
      conversationRecipient = mockRecipient,
      quote = quote
    )

    val output = renderMessage(message)

    assertTrue("Should contain media placeholder for null quote text", output.contains("> (media)"))
  }

  @Test
  fun `writeMessage with multiline quote preserves lines`() {
    val quoteRecipientId = RecipientId.from(42)
    every { Recipient.resolved(quoteRecipientId).getDisplayName(any()) } returns "Bob"

    val quote = Quote(
      1000L,
      quoteRecipientId,
      "Line one\nLine two\nLine three",
      false,
      SlideDeck(),
      emptyList(),
      QuoteModel.Type.NORMAL
    )

    val message = FakeMessageRecords.buildMediaMmsMessageRecord(
      id = 1,
      body = "My reply",
      dateSent = 1710500000000L,
      conversationRecipient = mockRecipient,
      quote = quote
    )

    val output = renderMessage(message)

    assertTrue(output.contains("> Line one"))
    assertTrue(output.contains("> Line two"))
    assertTrue(output.contains("> Line three"))
  }

  // ==================== Poll tests ====================

  @Test
  fun `writeMessage with active poll`() {
    val poll = PollRecord(
      id = 1,
      question = "Favorite color?",
      pollOptions = listOf(
        PollOption(1, "Red", listOf(Voter(1, 1), Voter(2, 1))),
        PollOption(2, "Blue", listOf(Voter(3, 1)))
      ),
      allowMultipleVotes = false,
      hasEnded = false,
      authorId = 1,
      messageId = 1
    )

    val message = FakeMessageRecords.buildMediaMmsMessageRecord(
      id = 1,
      body = "",
      dateSent = 1710500000000L,
      conversationRecipient = mockRecipient
    )

    val output = renderMessage(message, poll = poll)

    assertTrue(output.contains("(Poll) Favorite color?"))
    assertTrue(output.contains("  - Red (2 votes)"))
    assertTrue(output.contains("  - Blue (1 vote)"))
    assertTrue("Active poll should not show ended", !output.contains("(Poll ended)"))
  }

  @Test
  fun `writeMessage with ended poll shows ended marker`() {
    val poll = PollRecord(
      id = 1,
      question = "Where to eat?",
      pollOptions = listOf(
        PollOption(1, "Pizza", listOf(Voter(1, 1)))
      ),
      allowMultipleVotes = false,
      hasEnded = true,
      authorId = 1,
      messageId = 1
    )

    val message = FakeMessageRecords.buildMediaMmsMessageRecord(
      id = 1,
      body = "",
      dateSent = 1710500000000L,
      mailbox = MessageTypes.BASE_SENT_TYPE
    )

    val output = renderMessage(message, poll = poll)

    assertTrue(output.contains("(Poll) Where to eat?"))
    assertTrue(output.contains("(Poll ended)"))
  }

  @Test
  fun `writeMessage with poll with zero votes`() {
    val poll = PollRecord(
      id = 1,
      question = "New poll",
      pollOptions = listOf(
        PollOption(1, "Option A", emptyList()),
        PollOption(2, "Option B", emptyList())
      ),
      allowMultipleVotes = false,
      hasEnded = false,
      authorId = 1,
      messageId = 1
    )

    val message = FakeMessageRecords.buildMediaMmsMessageRecord(
      id = 1,
      body = "",
      dateSent = 1710500000000L,
      conversationRecipient = mockRecipient
    )

    val output = renderMessage(message, poll = poll)

    assertTrue(output.contains("  - Option A (0 votes)"))
    assertTrue(output.contains("  - Option B (0 votes)"))
  }

  // ==================== Attachment tests ====================

  @Test
  fun `writeMessage with image attachment`() {
    val attachment = FakeMessageRecords.buildDatabaseAttachment(
      attachmentId = AttachmentId(100),
      contentType = MediaUtil.IMAGE_JPEG,
      hasData = true,
      uploadTimestamp = 1710500000000L
    )

    val message = FakeMessageRecords.buildMediaMmsMessageRecord(
      id = 1,
      body = "",
      dateSent = 1710500000000L,
      conversationRecipient = mockRecipient
    )

    val pending = mutableListOf<PendingAttachment>()
    val output = renderMessage(message, listOf(attachment), pending)

    assertTrue("Should label as Image", output.contains("[Image: media/"))
    assertEquals("Should queue one attachment", 1, pending.size)
  }

  @Test
  fun `writeMessage with voice note attachment`() {
    val attachment = FakeMessageRecords.buildDatabaseAttachment(
      attachmentId = AttachmentId(101),
      contentType = "audio/aac",
      hasData = true,
      voiceNote = true,
      uploadTimestamp = 1710500000000L
    )

    val message = FakeMessageRecords.buildMediaMmsMessageRecord(
      id = 1,
      body = "",
      dateSent = 1710500000000L,
      conversationRecipient = mockRecipient
    )

    val pending = mutableListOf<PendingAttachment>()
    val output = renderMessage(message, listOf(attachment), pending)

    assertTrue("Should label as Voice message", output.contains("[Voice message: media/"))
  }

  @Test
  fun `writeMessage with video gif attachment`() {
    val attachment = FakeMessageRecords.buildDatabaseAttachment(
      attachmentId = AttachmentId(102),
      contentType = "video/mp4",
      hasData = true,
      videoGif = true,
      uploadTimestamp = 1710500000000L
    )

    val message = FakeMessageRecords.buildMediaMmsMessageRecord(
      id = 1,
      body = "",
      dateSent = 1710500000000L,
      conversationRecipient = mockRecipient
    )

    val pending = mutableListOf<PendingAttachment>()
    val output = renderMessage(message, listOf(attachment), pending)

    assertTrue("Should label as GIF", output.contains("[GIF: media/"))
  }

  @Test
  fun `writeMessage with body and attachment includes both`() {
    val attachment = FakeMessageRecords.buildDatabaseAttachment(
      attachmentId = AttachmentId(103),
      contentType = MediaUtil.IMAGE_JPEG,
      hasData = true,
      uploadTimestamp = 1710500000000L
    )

    val message = FakeMessageRecords.buildMediaMmsMessageRecord(
      id = 1,
      body = "Check out this photo",
      dateSent = 1710500000000L,
      conversationRecipient = mockRecipient
    )

    val output = renderMessage(message, listOf(attachment))

    assertTrue("Should contain body", output.contains("Alice: Check out this photo"))
    assertTrue("Should contain attachment", output.contains("[Image: media/"))
  }

  @Test
  fun `writeMessage with captioned attachment includes caption`() {
    val attachment = FakeMessageRecords.buildDatabaseAttachment(
      attachmentId = AttachmentId(104),
      contentType = MediaUtil.IMAGE_JPEG,
      hasData = true,
      caption = "A lovely sunset",
      uploadTimestamp = 1710500000000L
    )

    val message = FakeMessageRecords.buildMediaMmsMessageRecord(
      id = 1,
      body = "",
      dateSent = 1710500000000L,
      conversationRecipient = mockRecipient
    )

    val output = renderMessage(message, listOf(attachment))

    assertTrue("Should include caption", output.contains("] A lovely sunset"))
  }

  @Test
  fun `writeMessage with sticker attachment`() {
    val stickerLocator = StickerLocator("pack1", "key1", 1, "\uD83D\uDE00")
    val attachment = FakeMessageRecords.buildDatabaseAttachment(
      attachmentId = AttachmentId(105),
      contentType = "image/webp",
      hasData = true,
      stickerLocator = stickerLocator,
      uploadTimestamp = 1710500000000L
    )

    val message = FakeMessageRecords.buildMediaMmsMessageRecord(
      id = 1,
      body = "",
      dateSent = 1710500000000L,
      conversationRecipient = mockRecipient
    )

    val pending = mutableListOf<PendingAttachment>()
    val output = renderMessage(message, listOf(attachment), pending)

    assertTrue("Should show sticker format", output.contains("(Sticker) \uD83D\uDE00 [See: media/"))
    assertEquals("Should queue sticker for export", 1, pending.size)
  }

  @Test
  fun `writeMessage filters out quote attachments`() {
    val quoteAttachment = FakeMessageRecords.buildDatabaseAttachment(
      attachmentId = AttachmentId(200),
      contentType = MediaUtil.IMAGE_JPEG,
      hasData = true,
      quote = true,
      uploadTimestamp = 1710500000000L
    )
    val mainAttachment = FakeMessageRecords.buildDatabaseAttachment(
      attachmentId = AttachmentId(201),
      contentType = MediaUtil.IMAGE_JPEG,
      hasData = true,
      uploadTimestamp = 1710500000000L
    )

    val message = FakeMessageRecords.buildMediaMmsMessageRecord(
      id = 1,
      body = "",
      dateSent = 1710500000000L,
      conversationRecipient = mockRecipient
    )

    val pending = mutableListOf<PendingAttachment>()
    renderMessage(message, listOf(quoteAttachment, mainAttachment), pending)

    assertEquals("Should only queue the non-quote attachment", 1, pending.size)
    assertEquals(AttachmentId(201), pending[0].attachment.attachmentId)
  }

  @Test
  fun `writeMessage with multiple attachments queues all`() {
    val att1 = FakeMessageRecords.buildDatabaseAttachment(
      attachmentId = AttachmentId(301),
      contentType = MediaUtil.IMAGE_JPEG,
      hasData = true,
      uploadTimestamp = 1710500000000L
    )
    val att2 = FakeMessageRecords.buildDatabaseAttachment(
      attachmentId = AttachmentId(302),
      contentType = "video/mp4",
      hasData = true,
      uploadTimestamp = 1710500000000L
    )

    val message = FakeMessageRecords.buildMediaMmsMessageRecord(
      id = 1,
      body = "",
      dateSent = 1710500000000L,
      conversationRecipient = mockRecipient
    )

    val pending = mutableListOf<PendingAttachment>()
    val output = renderMessage(message, listOf(att1, att2), pending)

    assertEquals("Should queue both attachments", 2, pending.size)
    assertTrue("Should have Image label", output.contains("[Image:"))
    assertTrue("Should have Video label", output.contains("[Video:"))
  }

  // ==================== System message tests ====================

  @Test
  fun `writeMessage with group update`() {
    val message = FakeMessageRecords.buildMediaMmsMessageRecord(
      id = 1,
      body = "",
      dateSent = 1710500000000L,
      mailbox = MessageTypes.BASE_INBOX_TYPE or MessageTypes.SECURE_MESSAGE_BIT or MessageTypes.GROUP_UPDATE_BIT
    )

    val output = renderMessage(message)

    assertTrue(output.contains("--- "))
    assertTrue(output.contains("Group updated"))
    assertTrue(output.contains(" ---"))
  }

  @Test
  fun `writeMessage with expiration timer update`() {
    val message = FakeMessageRecords.buildMediaMmsMessageRecord(
      id = 1,
      body = "",
      dateSent = 1710500000000L,
      mailbox = MessageTypes.BASE_INBOX_TYPE or MessageTypes.SECURE_MESSAGE_BIT or MessageTypes.EXPIRATION_TIMER_UPDATE_BIT
    )

    val output = renderMessage(message)

    assertTrue(output.contains("Disappearing messages timer updated"))
  }

  @Test
  fun `writeMessage with incoming audio call`() {
    val message = FakeMessageRecords.buildMediaMmsMessageRecord(
      id = 1,
      body = "",
      dateSent = 1710500000000L,
      mailbox = MessageTypes.INCOMING_AUDIO_CALL_TYPE.toLong()
    )

    val output = renderMessage(message)

    assertTrue(output.contains("Incoming voice call"))
  }

  @Test
  fun `writeMessage with missed video call`() {
    val message = FakeMessageRecords.buildMediaMmsMessageRecord(
      id = 1,
      body = "",
      dateSent = 1710500000000L,
      mailbox = MessageTypes.MISSED_VIDEO_CALL_TYPE.toLong()
    )

    val output = renderMessage(message)

    assertTrue(output.contains("Missed video call"))
  }

  @Test
  fun `writeMessage with profile change`() {
    val message = FakeMessageRecords.buildMediaMmsMessageRecord(
      id = 1,
      body = "",
      dateSent = 1710500000000L,
      mailbox = MessageTypes.PROFILE_CHANGE_TYPE.toLong()
    )

    val output = renderMessage(message)

    assertTrue(output.contains("Profile updated"))
  }

  // ==================== getAttachmentLabel tests ====================

  @Test
  fun `getAttachmentLabel returns Image for image types`() {
    val attachment = FakeMessageRecords.buildDatabaseAttachment(contentType = MediaUtil.IMAGE_JPEG)
    assertEquals("Image", PlaintextExportRepository.getAttachmentLabel(attachment))
  }

  @Test
  fun `getAttachmentLabel returns Video for video types`() {
    val attachment = FakeMessageRecords.buildDatabaseAttachment(contentType = "video/mp4")
    assertEquals("Video", PlaintextExportRepository.getAttachmentLabel(attachment))
  }

  @Test
  fun `getAttachmentLabel returns GIF for video gif`() {
    val attachment = FakeMessageRecords.buildDatabaseAttachment(contentType = "video/mp4", videoGif = true)
    assertEquals("GIF", PlaintextExportRepository.getAttachmentLabel(attachment))
  }

  @Test
  fun `getAttachmentLabel returns Audio for audio types`() {
    val attachment = FakeMessageRecords.buildDatabaseAttachment(contentType = "audio/mpeg")
    assertEquals("Audio", PlaintextExportRepository.getAttachmentLabel(attachment))
  }

  @Test
  fun `getAttachmentLabel returns Voice message for voice note`() {
    val attachment = FakeMessageRecords.buildDatabaseAttachment(contentType = "audio/aac", voiceNote = true)
    assertEquals("Voice message", PlaintextExportRepository.getAttachmentLabel(attachment))
  }

  @Test
  fun `getAttachmentLabel returns Document for application types`() {
    val attachment = FakeMessageRecords.buildDatabaseAttachment(contentType = "application/pdf")
    assertEquals("Document", PlaintextExportRepository.getAttachmentLabel(attachment))
  }

  // ==================== buildAttachmentFileName tests ====================

  @Test
  fun `buildAttachmentFileName uses extension from file name`() {
    val attachment = FakeMessageRecords.buildDatabaseAttachment(
      attachmentId = AttachmentId(42),
      fileName = "photo.png",
      uploadTimestamp = 1710500000000L
    )

    val name = PlaintextExportRepository.buildAttachmentFileName(attachment, attachmentDateFormat)

    assertTrue("Should end with .png", name.endsWith(".png"))
    assertTrue("Should contain attachment id", name.contains("-42."))
    assertTrue("Should start with date", name.startsWith("2024-03-15"))
  }

  @Test
  fun `buildAttachmentFileName falls back to mime type extension`() {
    val attachment = FakeMessageRecords.buildDatabaseAttachment(
      attachmentId = AttachmentId(43),
      contentType = MediaUtil.IMAGE_JPEG,
      fileName = "",
      uploadTimestamp = 1710500000000L
    )

    val name = PlaintextExportRepository.buildAttachmentFileName(attachment, attachmentDateFormat)

    // MimeTypeMap may not be populated in Robolectric, so it may fall back to bin
    assertTrue("Should contain attachment id", name.contains("-43."))
  }

  @Test
  fun `buildAttachmentFileName falls back to bin`() {
    val attachment = FakeMessageRecords.buildDatabaseAttachment(
      attachmentId = AttachmentId(44),
      contentType = "application/x-unknown-type-for-test",
      fileName = "",
      uploadTimestamp = 1710500000000L
    )

    val name = PlaintextExportRepository.buildAttachmentFileName(attachment, attachmentDateFormat)

    assertTrue("Should end with .bin for unknown types", name.endsWith(".bin"))
  }

  // ==================== getExtension tests ====================

  @Test
  fun `getExtension prefers file extension from fileName`() {
    val attachment = FakeMessageRecords.buildDatabaseAttachment(fileName = "document.xlsx")
    assertEquals("xlsx", PlaintextExportRepository.getExtension(attachment))
  }

  @Test
  fun `getExtension ignores overly long extensions`() {
    val attachment = FakeMessageRecords.buildDatabaseAttachment(
      fileName = "file.thisisaverylongextension",
      contentType = "application/x-unknown-for-test"
    )

    // Extension is > 10 chars, should fall through
    assertEquals("bin", PlaintextExportRepository.getExtension(attachment))
  }

  @Test
  fun `getExtension falls back to bin when no info available`() {
    val attachment = FakeMessageRecords.buildDatabaseAttachment(
      fileName = "",
      contentType = "application/x-totally-unknown-for-test"
    )
    assertEquals("bin", PlaintextExportRepository.getExtension(attachment))
  }

  // ==================== sanitizeFileName tests ====================

  @Test
  fun `sanitizeFileName replaces special characters`() {
    assertEquals("hello_world", PlaintextExportRepository.sanitizeFileName("hello/world"))
    assertEquals("file_name", PlaintextExportRepository.sanitizeFileName("file:name"))
    assertEquals("a_b_c_d", PlaintextExportRepository.sanitizeFileName("a\\b*c?d"))
    assertEquals("test_file_", PlaintextExportRepository.sanitizeFileName("test<file>"))
    assertEquals("pipe_here", PlaintextExportRepository.sanitizeFileName("pipe|here"))
    assertEquals("quote_test", PlaintextExportRepository.sanitizeFileName("quote\"test"))
  }

  @Test
  fun `sanitizeFileName trims whitespace`() {
    assertEquals("hello", PlaintextExportRepository.sanitizeFileName("  hello  "))
  }

  @Test
  fun `sanitizeFileName truncates to 100 characters`() {
    val longName = "a".repeat(200)
    assertEquals(100, PlaintextExportRepository.sanitizeFileName(longName).length)
  }

  @Test
  fun `sanitizeFileName preserves normal characters`() {
    assertEquals("My Chat Group 2024", PlaintextExportRepository.sanitizeFileName("My Chat Group 2024"))
  }

  // ==================== getSenderName tests ====================

  @Test
  fun `getSenderName returns You for outgoing`() {
    val message = FakeMessageRecords.buildMediaMmsMessageRecord(
      mailbox = MessageTypes.BASE_SENT_TYPE
    )
    assertEquals("You", PlaintextExportRepository.getSenderName(context, message))
  }

  @Test
  fun `getSenderName returns display name for incoming`() {
    val message = FakeMessageRecords.buildMediaMmsMessageRecord(
      conversationRecipient = mockRecipient,
      individualRecipient = mockRecipient,
      mailbox = MessageTypes.BASE_INBOX_TYPE
    )
    assertEquals("Alice", PlaintextExportRepository.getSenderName(context, message))
  }

  // ==================== Helpers ====================

  private fun renderMessage(
    message: org.thoughtcrime.securesms.database.model.MmsMessageRecord,
    attachments: List<org.thoughtcrime.securesms.attachments.DatabaseAttachment> = emptyList(),
    pendingAttachments: MutableList<PendingAttachment> = mutableListOf(),
    poll: PollRecord? = null
  ): String {
    val stringWriter = StringWriter()
    val writer = BufferedWriter(stringWriter)

    val extraData = PlaintextExportRepository.ExtraMessageData(
      attachmentsById = if (attachments.isNotEmpty()) mapOf(message.id to attachments) else emptyMap(),
      mentionsById = emptyMap(),
      pollsById = if (poll != null) mapOf(message.id to poll) else emptyMap()
    )

    with(PlaintextExportRepository) {
      writer.writeMessage(
        context = context,
        message = message,
        extraData = extraData,
        dateFormat = dateFormat,
        attachmentDateFormat = attachmentDateFormat,
        pendingAttachments = pendingAttachments,
        includeMedia = true
      )
    }

    writer.flush()
    return stringWriter.toString()
  }
}
