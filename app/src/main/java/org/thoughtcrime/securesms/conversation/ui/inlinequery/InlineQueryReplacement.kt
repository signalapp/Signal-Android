package org.thoughtcrime.securesms.conversation.ui.inlinequery

import android.content.Context

/**
 * Encapsulate how to replace a query with a user selected result.
 */
sealed class InlineQueryReplacement(@get:JvmName("isKeywordSearch") val keywordSearch: Boolean = false) {
  abstract fun toCharSequence(context: Context): CharSequence

  class Emoji(private val emoji: String, keywordSearch: Boolean) : InlineQueryReplacement(keywordSearch) {
    override fun toCharSequence(context: Context): CharSequence {
      return emoji
    }
  }
}
