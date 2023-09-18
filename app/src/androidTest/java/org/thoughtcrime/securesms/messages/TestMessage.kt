package org.thoughtcrime.securesms.messages

import org.whispersystems.signalservice.api.crypto.EnvelopeMetadata
import org.whispersystems.signalservice.internal.push.Content
import org.whispersystems.signalservice.internal.push.Envelope

data class TestMessage(
  val envelope: Envelope,
  val content: Content,
  val metadata: EnvelopeMetadata,
  val serverDeliveredTimestamp: Long
)
