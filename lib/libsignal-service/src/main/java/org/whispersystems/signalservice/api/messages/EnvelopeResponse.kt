package org.whispersystems.signalservice.api.messages

import org.signal.network.websocket.WebSocketRequestMessage
import org.whispersystems.signalservice.internal.push.Envelope

/**
 * Represents an envelope off the wire, paired with the metadata needed to process it.
 */
class EnvelopeResponse(
  val envelope: Envelope,
  val serverDeliveredTimestamp: Long,
  val websocketRequest: WebSocketRequestMessage
)
