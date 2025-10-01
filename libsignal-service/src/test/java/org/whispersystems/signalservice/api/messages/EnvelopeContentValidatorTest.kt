/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.messages

import okio.ByteString.Companion.toByteString
import org.junit.Test
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.internal.push.Content
import org.whispersystems.signalservice.internal.push.DataMessage
import org.whispersystems.signalservice.internal.push.Envelope

class EnvelopeContentValidatorTest {

  companion object {
    private val SELF_ACI = ServiceId.ACI.parseOrThrow("0a5ebe7e-9de7-41a5-a25f-6ace4f8e11d1")
  }

  @Test
  fun `validate - ensure mismatched timestamps are marked invalid`() {
    val envelope = Envelope(
      timestamp = 1234
    )

    val content = Content(
      dataMessage = DataMessage(
        timestamp = 12345
      )
    )

    val result = EnvelopeContentValidator.validate(envelope, content, SELF_ACI)
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

    val result = EnvelopeContentValidator.validate(Envelope(), content, SELF_ACI)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - ensure polls with a question exceeding 100 characters are marked invalid`() {
    val content = Content(
      dataMessage = DataMessage(
        pollCreate = DataMessage.PollCreate(
          question = "abcdefghijklmnopqrstuvwxyabcdefghijklmnopqrstuvwxyabcdefghijklmnopqrstuvwxyabcdefghijklmnopqrstuvwxyz",
          options = listOf("option1", "option2"),
          allowMultiple = true
        )
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(), content, SELF_ACI)
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

    val result = EnvelopeContentValidator.validate(Envelope(), content, SELF_ACI)
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

    val result = EnvelopeContentValidator.validate(Envelope(), content, SELF_ACI)
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

    val result = EnvelopeContentValidator.validate(Envelope(), content, SELF_ACI)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }

  @Test
  fun `validate - ensure poll terminate without timestamps are marked invalid `() {
    val content = Content(
      dataMessage = DataMessage(
        pollTerminate = DataMessage.PollTerminate()
      )
    )

    val result = EnvelopeContentValidator.validate(Envelope(), content, SELF_ACI)
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

    val result = EnvelopeContentValidator.validate(Envelope(), content, SELF_ACI)
    assert(result is EnvelopeContentValidator.Result.Invalid)
  }
}
