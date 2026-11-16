package org.thoughtcrime.securesms.keyboard.emoji.search

import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.thoughtcrime.securesms.database.EmojiSearchTable
import org.thoughtcrime.securesms.database.SignalDatabase

private const val MINIMUM_INLINE_QUERY_THRESHOLD = 2
private const val EMOJI_SEARCH_LIMIT = 50

private val NOT_PUNCTUATION = "[^\\p{Punct}]".toRegex()

class EmojiSearchRepository {

  private val emojiSearchTable: EmojiSearchTable = SignalDatabase.emojiSearch

  fun submitQuery(query: String, limit: Int = EMOJI_SEARCH_LIMIT): Single<List<String>> {
    val result = if (query.length >= MINIMUM_INLINE_QUERY_THRESHOLD && NOT_PUNCTUATION.matches(query.substring(query.lastIndex))) {
      Single.fromCallable { emojiSearchTable.query(query, limit) }
    } else {
      Single.just(emptyList())
    }

    return result.subscribeOn(Schedulers.io())
  }
}
