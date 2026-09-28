package org.whispersystems.signalservice.api.messages

import org.signal.core.util.kibiBytes
import kotlin.time.Duration.Companion.hours

/**
 * Size limits that messages must respect on the wire. Shared by the send path, the receive-side
 * content validation, and the app layer so that all three agree on a single set of numbers.
 */
object SignalServiceMessageLimits {
  /** The maximum size of an inlined text body we'll allow in a proto. Anything larger than this will need to be a long-text attachment. */
  @JvmField
  val MAX_INLINE_BODY_SIZE_BYTES: Int = 2.kibiBytes.bytes.toInt()

  /** The maximum number of bars we'll accept in a sender-provided audio wave form. Each bar is one byte. */
  const val MAX_AUDIO_WAVEFORM_BAR_COUNT: Int = 100

  /** The maximum duration we'll accept for a sender-provided audio attachment. Longer durations are clamped to this. */
  @JvmField
  val MAX_AUDIO_DURATION_SECONDS: Float = 24.hours.inWholeSeconds.toFloat()
}
