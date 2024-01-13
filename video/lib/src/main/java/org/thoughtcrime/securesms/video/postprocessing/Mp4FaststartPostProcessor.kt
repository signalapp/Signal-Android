/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.video.postprocessing

import com.google.common.io.ByteStreams
import org.signal.libsignal.media.Mp4Sanitizer
import org.signal.libsignal.media.SanitizedMetadata
import org.thoughtcrime.securesms.video.exceptions.VideoPostProcessingException
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.SequenceInputStream

/**
 * A post processor that takes a stream of bytes, and using [Mp4Sanitizer], moves the metadata to the front of the file.
 *
 * @property inputStreamFactory factory for the [InputStream]. May be called multiple times.
 * @property inputLength the exact stream of the [InputStream]
 */
class Mp4FaststartPostProcessor(private val inputStreamFactory: () -> InputStream, private val inputLength: Long) {
  fun process(): InputStream {
    val metadata: SanitizedMetadata? = Mp4Sanitizer.sanitize(inputStreamFactory(), inputLength)

    if (metadata?.sanitizedMetadata == null) {
      throw VideoPostProcessingException("Mp4Sanitizer could not parse media metadata!")
    }

    val inputStream = inputStreamFactory()
    inputStream.skip(metadata.dataOffset)
    return SequenceInputStream(ByteArrayInputStream(metadata.sanitizedMetadata), ByteStreams.limit(inputStream, metadata.dataLength))
  }

  fun processAndWriteTo(outputStream: OutputStream) {
    process().copyTo(outputStream)
  }

  companion object {
    const val TAG = "Mp4Faststart"
  }
}
