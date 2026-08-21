package org.whispersystems.signalservice.api.messages

import org.signal.core.util.kibiBytes

/**
 * Size limits that messages must respect on the wire. Shared by the send path, the receive-side
 * content validation, and the app layer so that all three agree on a single set of numbers.
 */
object SignalServiceMessageLimits {
  /** The maximum size of an inlined text body we'll allow in a proto. Anything larger than this will need to be a long-text attachment. */
  @JvmField
  val MAX_INLINE_BODY_SIZE_BYTES: Int = 2.kibiBytes.bytes.toInt()
}
