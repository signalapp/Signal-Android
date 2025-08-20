/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.messages

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
}
