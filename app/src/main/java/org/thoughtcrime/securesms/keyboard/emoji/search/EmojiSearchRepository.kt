package org.thoughtcrime.securesms.keyboard.emoji.search

import android.content.Context
import android.net.Uri
import org.signal.core.util.concurrent.SignalExecutors
import org.thoughtcrime.securesms.components.emoji.Emoji
import org.thoughtcrime.securesms.components.emoji.EmojiKeyboardProvider
import org.thoughtcrime.securesms.components.emoji.EmojiPageModel
import org.thoughtcrime.securesms.components.emoji.RecentEmojiPageModel
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.EmojiSearchDatabase
import org.thoughtcrime.securesms.emoji.EmojiSource

private const val MINIMUM_QUERY_THRESHOLD = 1
private const val EMOJI_SEARCH_LIMIT = 20

class EmojiSearchRepository(private val context: Context) {

  private val emojiSearchDatabase: EmojiSearchDatabase = DatabaseFactory.getEmojiSearchDatabase(context)

  fun submitQuery(query: String, consumer: (EmojiPageModel) -> Unit) {
    if (query.length < MINIMUM_QUERY_THRESHOLD) {
      consumer(RecentEmojiPageModel(context, EmojiKeyboardProvider.RECENT_STORAGE_KEY))
    } else {
      SignalExecutors.SERIAL.execute {
        val emoji: List<String> = emojiSearchDatabase.query(query, EMOJI_SEARCH_LIMIT)

        val variationMap: Map<String, String> = EmojiSource.latest.variationMap
        val emojiVariationSets: MutableMap<String, LinkedHashSet<String>> = mutableMapOf()

        variationMap
          .filterKeys { emoji.contains(it) }
          .forEach { (variation, canonical) ->
            val set: LinkedHashSet<String> = emojiVariationSets.getOrDefault(canonical, linkedSetOf())

            set.add(variation)
            emojiVariationSets[canonical] = set
          }

        val displayEmoji: List<Emoji> = emoji.map { canonical ->
          val variationSet: LinkedHashSet<String> = linkedSetOf(canonical).apply {
            addAll(emojiVariationSets.getOrDefault(canonical, linkedSetOf()))
          }

          Emoji(variationSet.toList())
        }

        consumer(EmojiSearchResultsPageModel(emoji, displayEmoji))
      }
    }
  }

  private class EmojiSearchResultsPageModel(
    private val emoji: List<String>,
    private val displayEmoji: List<Emoji>
  ) : EmojiPageModel {
    override fun getIconAttr(): Int = -1

    override fun getEmoji(): List<String> = emoji

    override fun getDisplayEmoji(): List<Emoji> = displayEmoji

    override fun getSpriteUri(): Uri? = null

    override fun isDynamic(): Boolean = false
  }
}
