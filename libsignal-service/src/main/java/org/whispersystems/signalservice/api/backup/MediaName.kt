/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.backup

import org.signal.core.util.Base64

/**
 * Represent a media name for the various types of media that can be archived.
 */
@JvmInline
value class MediaName(val name: String) {

  companion object {
    fun fromDigest(digest: ByteArray) = MediaName(Base64.encodeWithoutPadding(digest))
    fun fromDigestForThumbnail(digest: ByteArray) = MediaName("${Base64.encodeWithoutPadding(digest)}_thumbnail")
    fun forThumbnailFromMediaName(mediaName: String) = MediaName("${mediaName}_thumbnail")
  }

  fun toByteArray(): ByteArray {
    return name.toByteArray()
  }
}
