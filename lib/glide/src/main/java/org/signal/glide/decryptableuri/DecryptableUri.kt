/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.glide.decryptableuri

import android.net.Uri
import com.bumptech.glide.load.Key
import java.security.MessageDigest

/**
 * A Glide model for local content that may need decrypting.
 *
 * [contentType] is an optional hint describing the data at [uri]. When provided, it lets the loader skip looking the type up. It is excluded from
 * equality and cache keys, so it must match what a lookup for [uri] would return.
 */
class DecryptableUri @JvmOverloads constructor(
  val uri: Uri,
  val thumbnailTimeUs: Long = 0,
  val contentType: String? = null
) : Key {
  override fun updateDiskCacheKey(messageDigest: MessageDigest) {
    messageDigest.update(uri.toString().toByteArray())
    messageDigest.update(thumbnailTimeUs.toString().toByteArray())
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is DecryptableUri) return false
    return uri == other.uri && thumbnailTimeUs == other.thumbnailTimeUs
  }

  override fun hashCode(): Int {
    return 31 * uri.hashCode() + thumbnailTimeUs.hashCode()
  }

  override fun toString(): String {
    return "DecryptableUri(uri=$uri, thumbnailTimeUs=$thumbnailTimeUs, contentType=$contentType)"
  }
}
