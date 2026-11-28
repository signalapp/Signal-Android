/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.audio

import android.media.MediaFormat
import java.nio.ByteBuffer

/**
 * Wraps raw AAC-LC access units in ADTS headers so that many frames can be fed to a decoder in a single input buffer.
 */
internal class AdtsPacker private constructor(
  private val audioObjectType: Int,
  private val sampleRateIndex: Int,
  private val channelConfig: Int
) {

  private val header = ByteArray(HEADER_SIZE)

  /**
   * Writes an ADTS header followed by the remaining bytes of [payload] into [destination].
   *
   * @return false if the frame does not fit, in which case nothing is written.
   */
  fun pack(payload: ByteBuffer, destination: ByteBuffer): Boolean {
    val frameLength = HEADER_SIZE + payload.remaining()
    if (frameLength > MAX_FRAME_LENGTH || destination.remaining() < frameLength) {
      return false
    }

    header[0] = 0xFF.toByte()
    header[1] = 0xF1.toByte()
    header[2] = (((audioObjectType - 1) shl 6) or (sampleRateIndex shl 2) or (channelConfig shr 2)).toByte()
    header[3] = (((channelConfig and 3) shl 6) or (frameLength shr 11)).toByte()
    header[4] = ((frameLength shr 3) and 0xFF).toByte()
    header[5] = (((frameLength and 7) shl 5) or 0x1F).toByte()
    header[6] = 0xFC.toByte()

    destination.put(header)
    destination.put(payload)

    return true
  }

  companion object {
    const val HEADER_SIZE = 7

    private const val MAX_FRAME_LENGTH = (1 shl 13) - 1
    private const val AAC_LC = 2

    /**
     * @return a packer for the track, or null if it is not plain AAC-LC with an explicit sample rate and channel layout.
     */
    fun fromFormat(format: MediaFormat): AdtsPacker? {
      if (format.getString(MediaFormat.KEY_MIME) != MediaFormat.MIMETYPE_AUDIO_AAC) {
        return null
      }

      val csd = format.getByteBuffer("csd-0") ?: return null
      if (csd.remaining() < 2) {
        return null
      }

      val byte0 = csd.get(csd.position()).toInt() and 0xFF
      val byte1 = csd.get(csd.position() + 1).toInt() and 0xFF

      val audioObjectType = byte0 shr 3
      val sampleRateIndex = ((byte0 and 0x7) shl 1) or (byte1 shr 7)
      val channelConfig = (byte1 shr 3) and 0xF

      if (audioObjectType != AAC_LC || sampleRateIndex > 12 || channelConfig !in 1..2) {
        return null
      }

      return AdtsPacker(audioObjectType, sampleRateIndex, channelConfig)
    }
  }
}
