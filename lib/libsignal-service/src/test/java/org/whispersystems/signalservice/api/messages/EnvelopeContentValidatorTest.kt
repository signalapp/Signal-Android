/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.messages

import okio.ByteString.Companion.toByteString
import org.junit.Test
import org.signal.core.models.ServiceId
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.signal.libsignal.protocol.message.DecryptionErrorMessage
import org.whispersystems.signalservice.internal.push.AttachmentPointer
import org.whispersystems.signalservice.internal.push.BodyRange
import org.whispersystems.signalservice.internal.push.CallMessage
import org.whispersystems.signalservice.internal.push.Content
import org.whispersystems.signalservice.internal.push.DataMessage
import org.whispersystems.signalservice.internal.push.EditMessage
import org.whispersystems.signalservice.internal.push.Envelope
import org.whispersystems.signalservice.internal.push.StoryMessage
import org.whispersystems.signalservice.internal.push.SyncMessage
import org.whispersystems.signalservice.internal.push.TextAttachment

class EnvelopeContentValidatorTest {

  companion object {
    private val SELF_ACI = ServiceId.ACI.parseOrThrow("0a5ebe7e-9de7-41a5-a25f-6ace4f8e11d1")
    private val OTHER_ACI = ServiceId.ACI.parseOrThrow("11111111-9de7-41a5-a25f-6ace4f8e11d1")
  }

  @Test
  fun `validate - ensure mismatched timestamps are marked invalid`() {
    val envelope = Envelope(
      clientTimestamp = 1234
    )

    val content = Content(
      dataMessage = DataMessage(
        timestamp = 12345
      )
    )

    val result = EnvelopeContentValidator.validate(envelope, content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - ensure polls without a question are marked invalid`() {
    val content = Content(
      dataMessage = DataMessage(
        pollCreate = DataMessage.PollCreate(
          options = listOf("option1", "option2"),
          allowMultiple = true
        )
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(), content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - ensure polls with a question exceeding 200 characters are marked invalid`() {
    val content = Content(
      dataMessage = DataMessage(
        pollCreate = DataMessage.PollCreate(
          question = "abcdefghijklmnopqrstuvwxyabcdefghijklmnopqrstuvwxyabcdefghijklmnopqrstuvwxyabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyabcdefghijklmnopqrstuvwxyabcdefghijklmnopqrstuvwxyabcdefghijklmnopqrstuvwxyz",
          options = listOf("option1", "option2"),
          allowMultiple = true
        )
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(), content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - ensure polls without at least two options are marked invalid`() {
    val content = Content(
      dataMessage = DataMessage(
        pollCreate = DataMessage.PollCreate(
          question = "how are you?",
          options = listOf("option1"),
          allowMultiple = true
        )
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(), content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - ensure poll options that exceed 100 characters are marked invalid `() {
    val content = Content(
      dataMessage = DataMessage(
        pollCreate = DataMessage.PollCreate(
          question = "how are you",
          options = listOf("abcdefghijklmnopqrstuvwxyabcdefghijklmnopqrstuvwxyabcdefghijklmnopqrstuvwxyabcdefghijklmnopqrstuvwxyz", "option2"),
          allowMultiple = true
        )
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(), content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - ensure polls without an explicit allow multiple votes option are marked invalid `() {
    val content = Content(
      dataMessage = DataMessage(
        pollCreate = DataMessage.PollCreate(
          question = "how are you",
          options = listOf("option1", "option2")
        )
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(), content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - ensure poll terminate without timestamps are marked invalid `() {
    val content = Content(
      dataMessage = DataMessage(
        pollTerminate = DataMessage.PollTerminate()
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(), content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - ensure poll votes without a valid aci are marked invalid`() {
    val content = Content(
      dataMessage = DataMessage(
        pollVote = DataMessage.PollVote(
          targetAuthorAciBinary = "bad".toByteArray().toByteString()
        )
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(), content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - plaintext content via envelope type with valid decryption error message is valid`() {
    val envelope = Envelope(
      type = Envelope.Type.PLAINTEXT_CONTENT
    )

    val content = Content(
      decryptionErrorMessage = createValidDecryptionErrorMessage()
    )

    val result = EnvelopeContentValidator.validate(envelope, content, SELF_ACI, CiphertextMessage.PLAINTEXT_CONTENT_TYPE)
    assert(result is EnvelopeContentValidator.Result.Valid)
  }

  @Test
  fun `validate - plaintext content via ciphertext message type (sealed sender) with valid decryption error message is valid`() {
    val envelope = Envelope(
      type = Envelope.Type.UNIDENTIFIED_SENDER
    )

    val content = Content(
      decryptionErrorMessage = createValidDecryptionErrorMessage()
    )

    val result = EnvelopeContentValidator.validate(envelope, content, SELF_ACI, CiphertextMessage.PLAINTEXT_CONTENT_TYPE)
    assert(result is EnvelopeContentValidator.Result.Valid)
  }

  @Test
  fun `validate - plaintext content via envelope type with unexpected DataMessage is invalid`() {
    val envelope = Envelope(
      type = Envelope.Type.PLAINTEXT_CONTENT,
      clientTimestamp = 1234
    )

    val content = Content(
      dataMessage = DataMessage(timestamp = 1234)
    )

    val result = EnvelopeContentValidator.validate(envelope, content, SELF_ACI, CiphertextMessage.PLAINTEXT_CONTENT_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - plaintext content via ciphertext message type (sealed sender) with unexpected DataMessage is invalid`() {
    val envelope = Envelope(
      type = Envelope.Type.UNIDENTIFIED_SENDER,
      clientTimestamp = 1234
    )

    val content = Content(
      dataMessage = DataMessage(timestamp = 1234)
    )

    val result = EnvelopeContentValidator.validate(envelope, content, SELF_ACI, CiphertextMessage.PLAINTEXT_CONTENT_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - plaintext content via envelope type without DecryptionErrorMessage is invalid`() {
    val envelope = Envelope(
      type = Envelope.Type.PLAINTEXT_CONTENT
    )

    val content = Content()

    val result = EnvelopeContentValidator.validate(envelope, content, SELF_ACI, CiphertextMessage.PLAINTEXT_CONTENT_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - plaintext content via ciphertext message type (sealed sender) without DecryptionErrorMessage is invalid`() {
    val envelope = Envelope(
      type = Envelope.Type.UNIDENTIFIED_SENDER
    )

    val content = Content()

    val result = EnvelopeContentValidator.validate(envelope, content, SELF_ACI, CiphertextMessage.PLAINTEXT_CONTENT_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - plaintext content with SyncMessage is invalid`() {
    val envelope = Envelope(
      type = Envelope.Type.PLAINTEXT_CONTENT
    )

    val content = Content(
      syncMessage = org.whispersystems.signalservice.internal.push.SyncMessage()
    )

    val result = EnvelopeContentValidator.validate(envelope, content, SELF_ACI, CiphertextMessage.PLAINTEXT_CONTENT_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - regular encrypted message is not subject to plaintext validation`() {
    val envelope = Envelope(
      type = Envelope.Type.DOUBLE_RATCHET,
      clientTimestamp = 1234
    )

    val content = Content(
      dataMessage = DataMessage(timestamp = 1234)
    )

    val result = EnvelopeContentValidator.validate(envelope, content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Valid)
  }

  private fun createValidDecryptionErrorMessage(): okio.ByteString {
    val minimalSenderKeyContent = ByteArray(64)
    val decryptionErrorMessage = DecryptionErrorMessage.forOriginalMessage(
      minimalSenderKeyContent,
      CiphertextMessage.SENDERKEY_TYPE,
      System.currentTimeMillis(),
      1
    )
    return decryptionErrorMessage.serialize().toByteString()
  }

  @Test
  fun `validate - ensure poll votes with too many option indexes are marked invalid`() {
    val content = Content(
      dataMessage = DataMessage(
        timestamp = 1234,
        pollVote = DataMessage.PollVote(
          targetAuthorAciBinary = OTHER_ACI.toByteString(),
          targetSentTimestamp = 1000,
          voteCount = 1,
          optionIndexes = List(11) { 0 }
        )
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(clientTimestamp = 1234), content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - ensure poll votes with max option indexes are marked valid`() {
    val content = Content(
      dataMessage = DataMessage(
        timestamp = 1234,
        pollVote = DataMessage.PollVote(
          targetAuthorAciBinary = OTHER_ACI.toByteString(),
          targetSentTimestamp = 1000,
          voteCount = 1,
          optionIndexes = List(10) { it }
        )
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(clientTimestamp = 1234), content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Valid)
  }

  @Test
  fun `validate - ensure pin messages without a valid aci are marked invalid`() {
    val content = Content(
      dataMessage = DataMessage(
        timestamp = 1234,
        pinMessage = DataMessage.PinMessage(
          targetAuthorAciBinary = "bad".toByteArray().toByteString(),
          targetSentTimestamp = 1000,
          pinDurationSeconds = 60
        )
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(clientTimestamp = 1234), content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - ensure pin messages without a target timestamp are marked invalid`() {
    val content = Content(
      dataMessage = DataMessage(
        timestamp = 1234,
        pinMessage = DataMessage.PinMessage(
          targetAuthorAciBinary = OTHER_ACI.toByteString(),
          pinDurationSeconds = 60
        )
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(clientTimestamp = 1234), content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - ensure pin messages without a pin duration are marked invalid`() {
    val content = Content(
      dataMessage = DataMessage(
        timestamp = 1234,
        pinMessage = DataMessage.PinMessage(
          targetAuthorAciBinary = OTHER_ACI.toByteString(),
          targetSentTimestamp = 1000
        )
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(clientTimestamp = 1234), content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - ensure pin messages with pinDurationForever set to false but no seconds are marked invalid`() {
    val content = Content(
      dataMessage = DataMessage(
        timestamp = 1234,
        pinMessage = DataMessage.PinMessage(
          targetAuthorAciBinary = OTHER_ACI.toByteString(),
          targetSentTimestamp = 1000,
          pinDurationForever = false
        )
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(clientTimestamp = 1234), content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - ensure pin messages with a pin duration in seconds are marked valid`() {
    val content = Content(
      dataMessage = DataMessage(
        timestamp = 1234,
        pinMessage = DataMessage.PinMessage(
          targetAuthorAciBinary = OTHER_ACI.toByteString(),
          targetSentTimestamp = 1000,
          pinDurationSeconds = 60
        )
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(clientTimestamp = 1234), content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Valid)
  }

  @Test
  fun `validate - ensure pin messages with pinDurationForever set to true are marked valid`() {
    val content = Content(
      dataMessage = DataMessage(
        timestamp = 1234,
        pinMessage = DataMessage.PinMessage(
          targetAuthorAciBinary = OTHER_ACI.toByteString(),
          targetSentTimestamp = 1000,
          pinDurationForever = true
        )
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(clientTimestamp = 1234), content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Valid)
  }

  @Test
  fun `validate - ensure quote body range mentions with invalid aci are marked invalid`() {
    val content = Content(
      dataMessage = DataMessage(
        timestamp = 1234,
        quote = DataMessage.Quote(
          id = 1000,
          authorAci = OTHER_ACI.toString(),
          bodyRanges = listOf(
            BodyRange(start = 0, length = 1, mentionAci = "not-a-uuid")
          )
        )
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(clientTimestamp = 1234), content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - ensure quote body range mentions with valid aci are marked valid`() {
    val content = Content(
      dataMessage = DataMessage(
        timestamp = 1234,
        quote = DataMessage.Quote(
          id = 1000,
          authorAci = OTHER_ACI.toString(),
          text = "hello",
          bodyRanges = listOf(
            BodyRange(start = 0, length = 1, mentionAci = OTHER_ACI.toString())
          )
        )
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(clientTimestamp = 1234), content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Valid)
  }

  @Test
  fun `validate - ensure quote body range whose start plus length overflows is marked invalid`() {
    val content = Content(
      dataMessage = DataMessage(
        timestamp = 1234,
        quote = DataMessage.Quote(
          id = 1000,
          authorAci = OTHER_ACI.toString(),
          text = "hello",
          bodyRanges = listOf(
            BodyRange(start = 1, length = Int.MAX_VALUE, mentionAci = OTHER_ACI.toString())
          )
        )
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(clientTimestamp = 1234), content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - ensure quote body range extending past the end of the text is marked invalid`() {
    val content = Content(
      dataMessage = DataMessage(
        timestamp = 1234,
        quote = DataMessage.Quote(
          id = 1000,
          authorAci = OTHER_ACI.toString(),
          text = "hello",
          bodyRanges = listOf(
            BodyRange(start = 3, length = 10, style = BodyRange.Style.BOLD)
          )
        )
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(clientTimestamp = 1234), content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - ensure body range whose start plus length overflows is marked invalid`() {
    val content = Content(
      dataMessage = DataMessage(
        timestamp = 1234,
        body = "hello",
        bodyRanges = listOf(
          BodyRange(start = 1, length = Int.MAX_VALUE, mentionAci = OTHER_ACI.toString())
        )
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(clientTimestamp = 1234), content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - ensure body range extending past the end of the body is marked invalid`() {
    val content = Content(
      dataMessage = DataMessage(
        timestamp = 1234,
        body = "hello",
        bodyRanges = listOf(
          BodyRange(start = 3, length = 10, style = BodyRange.Style.BOLD)
        )
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(clientTimestamp = 1234), content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - ensure body range extending past the end of the body is marked valid when a long text attachment is present`() {
    val content = Content(
      dataMessage = DataMessage(
        timestamp = 1234,
        body = "hello",
        bodyRanges = listOf(
          BodyRange(start = 3, length = 10, style = BodyRange.Style.BOLD)
        ),
        attachments = listOf(
          AttachmentPointer(cdnKey = "abc", contentType = "text/x-signal-plain")
        )
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(clientTimestamp = 1234), content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Valid)
  }

  @Test
  fun `validate - ensure body range with negative start is marked invalid even when a long text attachment is present`() {
    val content = Content(
      dataMessage = DataMessage(
        timestamp = 1234,
        body = "hello",
        bodyRanges = listOf(
          BodyRange(start = -1, length = 10, style = BodyRange.Style.BOLD)
        ),
        attachments = listOf(
          AttachmentPointer(cdnKey = "abc", contentType = "text/x-signal-plain")
        )
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(clientTimestamp = 1234), content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - ensure body range that exactly covers the body is marked valid`() {
    val content = Content(
      dataMessage = DataMessage(
        timestamp = 1234,
        body = "hello",
        bodyRanges = listOf(
          BodyRange(start = 0, length = 5, style = BodyRange.Style.BOLD)
        )
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(clientTimestamp = 1234), content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Valid)
  }

  @Test
  fun `validate - ensure style body range missing start is marked invalid`() {
    val content = Content(
      dataMessage = DataMessage(
        timestamp = 1234,
        bodyRanges = listOf(
          BodyRange(length = 1, style = BodyRange.Style.BOLD)
        )
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(clientTimestamp = 1234), content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - ensure style body range missing length is marked invalid`() {
    val content = Content(
      dataMessage = DataMessage(
        timestamp = 1234,
        bodyRanges = listOf(
          BodyRange(start = 0, style = BodyRange.Style.BOLD)
        )
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(clientTimestamp = 1234), content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - ensure style body range with both start and length is marked valid`() {
    val content = Content(
      dataMessage = DataMessage(
        timestamp = 1234,
        body = "hello",
        bodyRanges = listOf(
          BodyRange(start = 0, length = 1, style = BodyRange.Style.BOLD)
        )
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(clientTimestamp = 1234), content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Valid)
  }

  @Test
  fun `validate - ensure quote style body range missing start is marked invalid`() {
    val content = Content(
      dataMessage = DataMessage(
        timestamp = 1234,
        quote = DataMessage.Quote(
          id = 1000,
          authorAci = OTHER_ACI.toString(),
          bodyRanges = listOf(
            BodyRange(length = 1, style = BodyRange.Style.BOLD)
          )
        )
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(clientTimestamp = 1234), content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - ensure sync blocked with invalid string aci and empty binary list is marked invalid`() {
    val envelope = Envelope(sourceServiceId = SELF_ACI.toString())
    val content = Content(
      syncMessage = SyncMessage(
        blocked = SyncMessage.Blocked(
          acis = listOf("not-a-uuid"),
          acisBinary = emptyList()
        )
      )
    )

    val result = EnvelopeContentValidator.validate(envelope, content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - ensure sync blocked with invalid binary aci and empty string list is marked invalid`() {
    val envelope = Envelope(sourceServiceId = SELF_ACI.toString())
    val content = Content(
      syncMessage = SyncMessage(
        blocked = SyncMessage.Blocked(
          acis = emptyList(),
          acisBinary = listOf("bad".toByteArray().toByteString())
        )
      )
    )

    val result = EnvelopeContentValidator.validate(envelope, content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - ensure sync blocked with valid acis is marked valid`() {
    val envelope = Envelope(sourceServiceId = SELF_ACI.toString())
    val content = Content(
      syncMessage = SyncMessage(
        blocked = SyncMessage.Blocked(
          acis = listOf(OTHER_ACI.toString()),
          acisBinary = emptyList()
        )
      )
    )

    val result = EnvelopeContentValidator.validate(envelope, content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Valid)
  }

  @Test
  fun `validate - ensure story body range with negative length is marked invalid`() {
    val content = Content(
      storyMessage = StoryMessage(
        textAttachment = TextAttachment(text = "abc"),
        bodyRanges = listOf(
          BodyRange(start = 2, length = -3, style = BodyRange.Style.BOLD)
        )
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(clientTimestamp = 1234), content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - ensure story body range with negative start is marked invalid`() {
    val content = Content(
      storyMessage = StoryMessage(
        textAttachment = TextAttachment(text = "abc"),
        bodyRanges = listOf(
          BodyRange(start = -1, length = 1, style = BodyRange.Style.BOLD)
        )
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(clientTimestamp = 1234), content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - ensure story body range extending past the end of the text is marked invalid`() {
    val content = Content(
      storyMessage = StoryMessage(
        textAttachment = TextAttachment(text = "abc"),
        bodyRanges = listOf(
          BodyRange(start = 2, length = 10, style = BodyRange.Style.BOLD)
        )
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(clientTimestamp = 1234), content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - ensure story style body range missing length is marked invalid`() {
    val content = Content(
      storyMessage = StoryMessage(
        textAttachment = TextAttachment(text = "abc"),
        bodyRanges = listOf(
          BodyRange(start = 0, style = BodyRange.Style.BOLD)
        )
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(clientTimestamp = 1234), content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - ensure story body range that exactly covers the text is marked valid`() {
    val content = Content(
      storyMessage = StoryMessage(
        textAttachment = TextAttachment(text = "abc"),
        bodyRanges = listOf(
          BodyRange(start = 0, length = 3, style = BodyRange.Style.BOLD)
        )
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(clientTimestamp = 1234), content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Valid)
  }

  @Test
  fun `validate - ensure story context with a valid author but missing sentTimestamp is marked invalid`() {
    val content = Content(
      dataMessage = DataMessage(
        timestamp = 1234,
        reaction = DataMessage.Reaction(
          emoji = "👍",
          targetAuthorAci = OTHER_ACI.toString(),
          targetSentTimestamp = 1
        ),
        storyContext = DataMessage.StoryContext(
          authorAci = OTHER_ACI.toString()
        )
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(clientTimestamp = 1234), content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - ensure story context with a valid author and sentTimestamp is marked valid`() {
    val content = Content(
      dataMessage = DataMessage(
        timestamp = 1234,
        reaction = DataMessage.Reaction(
          emoji = "👍",
          targetAuthorAci = OTHER_ACI.toString(),
          targetSentTimestamp = 1
        ),
        storyContext = DataMessage.StoryContext(
          authorAci = OTHER_ACI.toString(),
          sentTimestamp = 1000
        )
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(clientTimestamp = 1234), content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Valid)
  }

  @Test
  fun `validate - ensure edit message with mismatched nested timestamp is marked invalid`() {
    val content = Content(
      editMessage = EditMessage(
        targetSentTimestamp = 1000,
        dataMessage = DataMessage(timestamp = 5678)
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(clientTimestamp = 1234), content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - ensure edit message with missing nested timestamp is marked invalid`() {
    val content = Content(
      editMessage = EditMessage(
        targetSentTimestamp = 1000,
        dataMessage = DataMessage()
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(clientTimestamp = 1234), content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - ensure edit message with nested timestamp matching the envelope is marked valid`() {
    val content = Content(
      editMessage = EditMessage(
        targetSentTimestamp = 1000,
        dataMessage = DataMessage(timestamp = 1234)
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(clientTimestamp = 1234), content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Valid)
  }

  @Test
  fun `validate - ensure data message body of exactly 2048 bytes is marked valid`() {
    val content = Content(
      dataMessage = DataMessage(
        timestamp = 1234,
        body = "a".repeat(2048)
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(clientTimestamp = 1234), content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Valid)
  }

  @Test
  fun `validate - ensure data message body over 2048 bytes is marked invalid`() {
    val content = Content(
      dataMessage = DataMessage(
        timestamp = 1234,
        body = "a".repeat(2049)
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(clientTimestamp = 1234), content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - ensure data message body over 2048 UTF-8 bytes is marked invalid`() {
    val content = Content(
      dataMessage = DataMessage(
        timestamp = 1234,
        body = "é".repeat(1025)
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(clientTimestamp = 1234), content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - ensure edit message body over 2048 bytes is marked invalid`() {
    val content = Content(
      editMessage = EditMessage(
        targetSentTimestamp = 1000,
        dataMessage = DataMessage(
          timestamp = 1234,
          body = "a".repeat(2049)
        )
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(clientTimestamp = 1234), content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - ensure sync sent body over 2048 bytes is marked invalid`() {
    val envelope = Envelope(sourceServiceId = SELF_ACI.toString(), clientTimestamp = 1234)
    val content = Content(
      syncMessage = SyncMessage(
        sent = SyncMessage.Sent(
          timestamp = 1234,
          destinationServiceId = OTHER_ACI.toString(),
          message = DataMessage(
            timestamp = 1234,
            body = "a".repeat(2049)
          )
        )
      )
    )

    val result = EnvelopeContentValidator.validate(envelope, content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - ensure sync sent without a timestamp is marked invalid`() {
    val envelope = Envelope(sourceServiceId = SELF_ACI.toString(), clientTimestamp = 1234)
    val content = Content(
      syncMessage = SyncMessage(
        sent = SyncMessage.Sent(
          destinationServiceId = OTHER_ACI.toString(),
          message = DataMessage(timestamp = 1234)
        )
      )
    )

    val result = EnvelopeContentValidator.validate(envelope, content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - ensure sync sent with a timestamp and valid destination is marked valid`() {
    val envelope = Envelope(sourceServiceId = SELF_ACI.toString(), clientTimestamp = 1234)
    val content = Content(
      syncMessage = SyncMessage(
        sent = SyncMessage.Sent(
          timestamp = 1234,
          destinationServiceId = OTHER_ACI.toString(),
          message = DataMessage(timestamp = 1234)
        )
      )
    )

    val result = EnvelopeContentValidator.validate(envelope, content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Valid)
  }

  @Test
  fun `validate - ensure sync sent with story recipients but no story message and not a recipient update is marked invalid`() {
    val envelope = Envelope(sourceServiceId = SELF_ACI.toString(), clientTimestamp = 1234)
    val content = Content(
      syncMessage = SyncMessage(
        sent = SyncMessage.Sent(
          timestamp = 1234,
          storyMessageRecipients = listOf(
            SyncMessage.Sent.StoryMessageRecipient(
              destinationServiceId = OTHER_ACI.toString(),
              isAllowedToReply = true
            )
          )
        )
      )
    )

    val result = EnvelopeContentValidator.validate(envelope, content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - ensure sync sent story recipient update without a story message is marked valid`() {
    val envelope = Envelope(sourceServiceId = SELF_ACI.toString(), clientTimestamp = 1234)
    val content = Content(
      syncMessage = SyncMessage(
        sent = SyncMessage.Sent(
          timestamp = 1234,
          isRecipientUpdate = true,
          storyMessageRecipients = listOf(
            SyncMessage.Sent.StoryMessageRecipient(
              destinationServiceId = OTHER_ACI.toString(),
              isAllowedToReply = true
            )
          )
        )
      )
    )

    val result = EnvelopeContentValidator.validate(envelope, content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Valid)
  }

  @Test
  fun `validate - ensure sync sent story recipient with an invalid destination is marked invalid`() {
    val envelope = Envelope(sourceServiceId = SELF_ACI.toString(), clientTimestamp = 1234)
    val content = Content(
      syncMessage = SyncMessage(
        sent = SyncMessage.Sent(
          timestamp = 1234,
          storyMessage = StoryMessage(),
          storyMessageRecipients = listOf(
            SyncMessage.Sent.StoryMessageRecipient(
              destinationServiceId = "not-a-uuid",
              isAllowedToReply = true
            )
          )
        )
      )
    )

    val result = EnvelopeContentValidator.validate(envelope, content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - ensure sync sent story recipient without isAllowedToReply is marked invalid`() {
    val envelope = Envelope(sourceServiceId = SELF_ACI.toString(), clientTimestamp = 1234)
    val content = Content(
      syncMessage = SyncMessage(
        sent = SyncMessage.Sent(
          timestamp = 1234,
          storyMessage = StoryMessage(),
          storyMessageRecipients = listOf(
            SyncMessage.Sent.StoryMessageRecipient(
              destinationServiceId = OTHER_ACI.toString()
            )
          )
        )
      )
    )

    val result = EnvelopeContentValidator.validate(envelope, content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - ensure sync read without a timestamp is marked invalid`() {
    val envelope = Envelope(sourceServiceId = SELF_ACI.toString())
    val content = Content(
      syncMessage = SyncMessage(
        read = listOf(
          SyncMessage.Read(senderAci = OTHER_ACI.toString())
        )
      )
    )

    val result = EnvelopeContentValidator.validate(envelope, content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - ensure sync read with a valid aci and timestamp is marked valid`() {
    val envelope = Envelope(sourceServiceId = SELF_ACI.toString())
    val content = Content(
      syncMessage = SyncMessage(
        read = listOf(
          SyncMessage.Read(senderAci = OTHER_ACI.toString(), timestamp = 1000)
        )
      )
    )

    val result = EnvelopeContentValidator.validate(envelope, content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Valid)
  }

  @Test
  fun `validate - ensure sync outgoing payment with mobileCoin missing required fields is marked invalid`() {
    val envelope = Envelope(sourceServiceId = SELF_ACI.toString())
    val content = Content(
      syncMessage = SyncMessage(
        outgoingPayment = SyncMessage.OutgoingPayment(
          recipientServiceId = OTHER_ACI.toString(),
          mobileCoin = SyncMessage.OutgoingPayment.MobileCoin(
            recipientAddress = "address".toByteArray().toByteString(),
            amountPicoMob = 1,
            feePicoMob = 1,
            receipt = "receipt".toByteArray().toByteString()
          )
        )
      )
    )

    val result = EnvelopeContentValidator.validate(envelope, content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - ensure sync outgoing payment with all mobileCoin fields is marked valid`() {
    val envelope = Envelope(sourceServiceId = SELF_ACI.toString())
    val content = Content(
      syncMessage = SyncMessage(
        outgoingPayment = SyncMessage.OutgoingPayment(
          recipientServiceId = OTHER_ACI.toString(),
          mobileCoin = SyncMessage.OutgoingPayment.MobileCoin(
            recipientAddress = "address".toByteArray().toByteString(),
            amountPicoMob = 1,
            feePicoMob = 1,
            receipt = "receipt".toByteArray().toByteString(),
            ledgerBlockIndex = 1
          )
        )
      )
    )

    val result = EnvelopeContentValidator.validate(envelope, content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Valid)
  }

  @Test
  fun `validate - ensure one-to-one call event with a malformed conversationId is marked invalid`() {
    val envelope = Envelope(sourceServiceId = SELF_ACI.toString())
    val content = Content(
      syncMessage = SyncMessage(
        callEvent = SyncMessage.CallEvent(
          callId = 1,
          type = SyncMessage.CallEvent.Type.AUDIO_CALL,
          conversationId = "bad".toByteArray().toByteString()
        )
      )
    )

    val result = EnvelopeContentValidator.validate(envelope, content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - ensure one-to-one call event with a valid aci conversationId is marked valid`() {
    val envelope = Envelope(sourceServiceId = SELF_ACI.toString())
    val content = Content(
      syncMessage = SyncMessage(
        callEvent = SyncMessage.CallEvent(
          callId = 1,
          type = SyncMessage.CallEvent.Type.AUDIO_CALL,
          conversationId = OTHER_ACI.toByteString()
        )
      )
    )

    val result = EnvelopeContentValidator.validate(envelope, content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Valid)
  }

  @Test
  fun `validate - ensure observed ad-hoc call event without a timestamp is marked invalid`() {
    val envelope = Envelope(sourceServiceId = SELF_ACI.toString())
    val content = Content(
      syncMessage = SyncMessage(
        callEvent = SyncMessage.CallEvent(
          callId = 1,
          type = SyncMessage.CallEvent.Type.AD_HOC_CALL,
          event = SyncMessage.CallEvent.Event.OBSERVED,
          direction = SyncMessage.CallEvent.Direction.INCOMING,
          conversationId = "room".toByteArray().toByteString()
        )
      )
    )

    val result = EnvelopeContentValidator.validate(envelope, content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - ensure observed ad-hoc call event with a timestamp is marked valid`() {
    val envelope = Envelope(sourceServiceId = SELF_ACI.toString())
    val content = Content(
      syncMessage = SyncMessage(
        callEvent = SyncMessage.CallEvent(
          callId = 1,
          type = SyncMessage.CallEvent.Type.AD_HOC_CALL,
          event = SyncMessage.CallEvent.Event.OBSERVED,
          direction = SyncMessage.CallEvent.Direction.INCOMING,
          conversationId = "room".toByteArray().toByteString(),
          timestamp = 1000
        )
      )
    )

    val result = EnvelopeContentValidator.validate(envelope, content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Valid)
  }

  @Test
  fun `validate - ensure call message hangup without a type is marked invalid`() {
    val content = Content(
      callMessage = CallMessage(
        hangup = CallMessage.Hangup(id = 1)
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(), content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - ensure call message hangup with a type is marked valid`() {
    val content = Content(
      callMessage = CallMessage(
        hangup = CallMessage.Hangup(id = 1, type = CallMessage.Hangup.Type.HANGUP_NORMAL)
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(), content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Valid)
  }

  @Test
  fun `validate - ensure call message without a hangup is marked valid`() {
    val content = Content(
      callMessage = CallMessage(
        busy = CallMessage.Busy(id = 1)
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(), content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Valid)
  }

  @Test
  fun `validate - ensure a sync message with no source is marked invalid rather than throwing`() {
    val content = Content(
      syncMessage = SyncMessage()
    )

    val result = EnvelopeContentValidator.validate(Envelope(), content, SELF_ACI, CiphertextMessage.WHISPER_TYPE)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }
}
