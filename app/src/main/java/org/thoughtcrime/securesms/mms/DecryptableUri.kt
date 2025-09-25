/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mms

import android.net.Uri
import com.bumptech.glide.load.Key
import java.security.MessageDigest

data class DecryptableUri(val uri: Uri) : Key {
  override fun updateDiskCacheKey(messageDigest: MessageDigest) {
    messageDigest.update(uri.toString().toByteArray())
  }
}
