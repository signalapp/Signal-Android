package org.thoughtcrime.securesms.components.emoji.parsing

import org.thoughtcrime.securesms.emoji.EmojiPage
import org.thoughtcrime.securesms.util.Hex
import java.nio.charset.Charset

data class EmojiDrawInfo(val page: EmojiPage, val index: Int, private val emoji: String) {
  val rawEmoji: String
    get() {
      val emojiBytes: ByteArray = emoji.toByteArray(Charset.forName("UTF-16"))
      return Hex.toStringCondensed(emojiBytes.slice(2 until emojiBytes.size).toByteArray())
    }
}
