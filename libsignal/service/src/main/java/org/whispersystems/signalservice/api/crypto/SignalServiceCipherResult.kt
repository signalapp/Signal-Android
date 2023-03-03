package org.whispersystems.signalservice.api.crypto

import org.whispersystems.signalservice.internal.push.SignalServiceProtos

/**
 * Represents the output of decrypting a [SignalServiceProtos.Envelope] via [SignalServiceCipher.decrypt]
 *
 * @param content The [SignalServiceProtos.Content] that was decrypted from the envelope.
 * @param metadata The decrypted metadata of the envelope. Represents sender information that may have
 *                 been encrypted with sealed sender.
 */
data class SignalServiceCipherResult(
  val content: SignalServiceProtos.Content,
  val metadata: EnvelopeMetadata
)
