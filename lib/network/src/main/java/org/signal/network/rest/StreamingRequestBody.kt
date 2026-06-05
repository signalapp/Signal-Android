/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.rest

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.InputStream

/**
 * A [RequestBody] that streams its content from an [InputStream] rather than buffering it all in
 * memory. Suitable for uploading large files / blobs through [SignalRestClient.request].
 *
 * Pair with a [org.whispersystems.signalservice.api.messages.SignalServiceAttachment.ProgressListener]
 * passed to `request()` to receive upload progress as bytes flow.
 *
 * @param source The data to upload. Will be read once and not closed by this body; callers should
 *               close it themselves (or wrap it in a stream that closes the underlying resource).
 * @param contentLength The total number of bytes to upload, or `-1` if unknown (causes chunked
 *                      transfer encoding). Progress reporting requires a known length.
 * @param contentType Optional `Content-Type` for the body, e.g. `"application/octet-stream"`.
 */
class StreamingRequestBody(
  private val source: InputStream,
  private val contentLength: Long,
  private val contentType: String? = null
) : RequestBody() {

  override fun contentType(): MediaType? = contentType?.toMediaTypeOrNull()

  override fun contentLength(): Long = contentLength

  /** [InputStream] cannot be rewound, so retries that require replaying the body are not safe. */
  override fun isOneShot(): Boolean = true

  override fun writeTo(sink: BufferedSink) {
    source.source().use { okSource ->
      sink.writeAll(okSource)
    }
  }
}
