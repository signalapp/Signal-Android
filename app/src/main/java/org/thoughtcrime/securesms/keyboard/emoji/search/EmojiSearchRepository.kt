package org.thoughtcrime.securesms.keyboard.emoji.search

import android.content.Context
import android.net.Uri
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.concurrent.SignalExecutors
import org.thoughtcrime.securesms.components.emoji.Emoji
import org.thoughtcrime.securesms.components.emoji.EmojiPageModel
import org.thoughtcrime.securesms.components.emoji.RecentEmojiPageModel
import org.thoughtcrime.securesms.database.EmojiSearchTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.emoji.EmojiSource
import org.thoughtcrime.securesms.util.TextSecurePreferences
import java.util.function.Consumer

private const val MINIMUM_QUERY_THRESHOLD = 1
private const val MINIMUM_INLINE_QUERY_THRESHOLD = 2
private const val EMOJI_SEARCH_LIMIT = 50

private val NOT_PUNCTUATION = "[^\\p{Punct}]".toRegex()

class EmojiSearchRepository(private val context: Context) {

  private val emojiSearchTable: EmojiSearchTable = SignalDatabase.emojiSearch

  fun submitQuery(query: String, limit: Int = EMOJI_SEARCH_LIMIT): Single<List<String>> {
    val result = if (query.length >= MINIMUM_INLINE_QUERY_THRESHOLD && NOT_PUNCTUATION.matches(query.substring(query.lastIndex))) {
      Single.fromCallable { emojiSearchTable.query(query, limit) }
    } else {
      Single.just(emptyList())
    }

    return result.subscribeOn(Schedulers.io())
  }

  fun submitQuery(query: String, includeRecents: Boolean, limit: Int = EMOJI_SEARCH_LIMIT, consumer: Consumer<EmojiPageModel>) {
    if (query.length < MINIMUM_QUERY_THRESHOLD && includeRecents) {
      consumer.accept(RecentEmojiPageModel(context, TextSecurePreferences.RECENT_STORAGE_KEY))
    } else {
      SignalExecutors.SERIAL.execute {
        val emoji: List<String> = emojiSearchTable.query(query, limit)

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
