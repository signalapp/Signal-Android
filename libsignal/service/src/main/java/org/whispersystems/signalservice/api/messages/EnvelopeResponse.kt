package org.whispersystems.signalservice.api.messages

import org.whispersystems.signalservice.internal.push.Envelope
import org.whispersystems.signalservice.internal.websocket.WebSocketRequestMessage

/**
 * Represents an envelope off the wire, paired with the metadata needed to process it.
 */
class EnvelopeResponse(
  val envelope: Envelope,
  val serverDeliveredTimestamp: Long,
  val websocketRequest: WebSocketRequestMessage
)
