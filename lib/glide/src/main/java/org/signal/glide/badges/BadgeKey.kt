/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.glide.badges

import com.bumptech.glide.load.Key
import org.signal.core.models.badges.Badge
import java.security.MessageDigest

/**
 * Cache key for a badge sprite sheet, distinguished only by the fields that affect the image.
 */
data class BadgeKey(val badge: Badge) : Key {
  override fun updateDiskCacheKey(messageDigest: MessageDigest) {
    messageDigest.update(badge.id.toByteArray(Key.CHARSET))
    messageDigest.update(badge.imageUrl.toString().toByteArray(Key.CHARSET))
    messageDigest.update(badge.imageDensity.toByteArray(Key.CHARSET))
  }
}
