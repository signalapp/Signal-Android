/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mms

import org.signal.core.util.limit
import org.signal.core.util.readAtMostNBytes
import org.thoughtcrime.securesms.glide.cache.WebpSanitizerCheck
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.SequenceInputStream

/**
 * Archived thumbnails are padded and have no plaintext length, so restored webps carry bytes past their RIFF size.
 */
object WebpPaddingRemover {

  private const val HEADER_SIZE = WebpSanitizerCheck.HEADER_SIZE

  @JvmStatic
  fun trim(source: InputStream): InputStream {
    val header = source.readAtMostNBytes(HEADER_SIZE)
    val prefix = ByteArrayInputStream(header)

    if (!WebpSanitizerCheck.isWebpHeader(header)) {
      return SequenceInputStream(prefix, source)
    }

    val remaining = WebpSanitizerCheck.declaredFileLength(header) - HEADER_SIZE
    return SequenceInputStream(prefix, source.limit(remaining.coerceAtLeast(0)))
  }
}
