package org.thoughtcrime.securesms.emoji

import android.net.Uri
import com.bumptech.glide.load.Key
import java.security.MessageDigest

typealias EmojiPageFactory = (Uri) -> EmojiPage

sealed class EmojiPage(open val uri: Uri) : Key {
  override fun updateDiskCacheKey(messageDigest: MessageDigest) {
    messageDigest.update("EmojiPage".encodeToByteArray())
    messageDigest.update(uri.toString().encodeToByteArray())
  }

  data class Asset(override val uri: Uri) : EmojiPage(uri)
  data class Disk(override val uri: Uri) : EmojiPage(uri)
}
