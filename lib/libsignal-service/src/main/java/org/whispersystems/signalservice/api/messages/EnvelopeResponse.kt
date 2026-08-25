package org.whispersystems.signalservice.api.messages

import org.signal.network.websocket.WebSocketRequestMessage
import org.whispersystems.signalservice.internal.push.Envelope

/**
 * Represents an envelope off the wire, paired with the metadata needed to process it.
 */
sealed class EnvelopeResponse {

  abstract val websocketRequest: WebSocketRequestMessage

  /** An envelope we successfully parsed and can hand off for processing. */
  class Parsed(
    val envelope: Envelope,
    val serverDeliveredTimestamp: Long,
    override val websocketRequest: WebSocketRequestMessage
  ) : EnvelopeResponse()

  /** An envelope whose body could not be parsed at all. There is nothing to process, but it still needs to be acked. */
  class Unparseable(
    override val websocketRequest: WebSocketRequestMessage
  ) : EnvelopeResponse()
}
