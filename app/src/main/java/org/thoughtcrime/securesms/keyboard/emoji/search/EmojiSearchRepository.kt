package org.thoughtcrime.securesms.keyboard.emoji.search

import android.content.Context
import android.net.Uri
import org.signal.core.util.concurrent.SignalExecutors
import org.thoughtcrime.securesms.components.emoji.Emoji
import org.thoughtcrime.securesms.components.emoji.EmojiPageModel
import org.thoughtcrime.securesms.components.emoji.RecentEmojiPageModel
import org.thoughtcrime.securesms.database.EmojiSearchDatabase
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.emoji.EmojiSource
import org.thoughtcrime.securesms.util.TextSecurePreferences
import java.util.function.Consumer

private const val MINIMUM_QUERY_THRESHOLD = 1
private const val EMOJI_SEARCH_LIMIT = 20

class EmojiSearchRepository(private val context: Context) {

  private val emojiSearchDatabase: EmojiSearchDatabase = SignalDatabase.emojiSearch

  fun submitQuery(query: String, includeRecents: Boolean, limit: Int = EMOJI_SEARCH_LIMIT, consumer: Consumer<EmojiPageModel>) {
    if (query.length < MINIMUM_QUERY_THRESHOLD && includeRecents) {
      consumer.accept(RecentEmojiPageModel(context, TextSecurePreferences.RECENT_STORAGE_KEY))
    } else {
      SignalExecutors.SERIAL.execute {
        val emoji: List<String> = emojiSearchDatabase.query(query, limit)

        val displayEmoji: List<Emoji> = emoji
          .mapNotNull { canonical -> EmojiSource.latest.canonicalToVariations[canonical] }
          .map { Emoji(it) }

        consumer.accept(EmojiSearchResultsPageModel(emoji, displayEmoji))
      }
    }
  }

  private class EmojiSearchResultsPageModel(
    private val emoji: List<String>,
    private val displayEmoji: List<Emoji>
  ) : EmojiPageModel {
    override fun getKey(): String = ""

    override fun getIconAttr(): Int = -1

    override fun getEmoji(): List<String> = emoji

    override fun getDisplayEmoji(): List<Emoji> = displayEmoji

    override fun getSpriteUri(): Uri? = null

    override fun isDynamic(): Boolean = false
  }
}
