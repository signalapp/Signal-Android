/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.backup

import org.signal.core.util.Hex

/**
 * Represent a media name for the various types of media that can be archived.
 */
@JvmInline
value class MediaName(val name: String) {

  companion object {
    fun fromDigest(digest: ByteArray) = MediaName(Hex.toStringCondensed(digest))
    fun fromDigestForThumbnail(digest: ByteArray) = MediaName("${Hex.toStringCondensed(digest)}_thumbnail")
    fun forThumbnailFromMediaName(mediaName: String) = MediaName("${mediaName}_thumbnail")
  }

  fun toByteArray(): ByteArray {
    return name.toByteArray()
  }

  override fun toString(): String {
    return name
  }
}
