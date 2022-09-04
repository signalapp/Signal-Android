package org.thoughtcrime.securesms.database

import android.content.Context
import androidx.core.content.contentValuesOf
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.thoughtcrime.securesms.database.model.EmojiSearchData
import org.thoughtcrime.securesms.util.CursorUtil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Contains all info necessary for full-text search of emoji tags.
 */
class EmojiSearchDatabase(context: Context, helper: SQLCipherOpenHelper) : Database(context, helper) {

  companion object {
    const val TABLE_NAME = "emoji_search"
    const val LABEL = "label"
    const val EMOJI = "emoji"
    const val CREATE_EMOJI_SEARCH_TABLE_COMMAND = "CREATE VIRTUAL TABLE $TABLE_NAME USING fts5($LABEL, $EMOJI UNINDEXED)"
  }

  /**
   * @param query A search query. Doesn't need any special formatted -- it'll be sanitized.
   * @return A list of emoji that are related to the search term, ordered by relevance.
   */
  fun query(originalQuery: String, originalLimit: Int): List<String> {
    val query: String = originalQuery.trim()

    if (query.isEmpty()) {
      return emptyList()
    }

    val limit: Int = max(originalLimit, 100)
    val entries = mutableListOf<Entry>()

    readableDatabase.query(TABLE_NAME, arrayOf(LABEL, EMOJI), "$LABEL LIKE ?", arrayOf("%$query%"), null, null, null, "$limit")
      .use { cursor ->
        while (cursor.moveToNext()) {
          entries += Entry(
            label = CursorUtil.requireString(cursor, LABEL),
            emoji = CursorUtil.requireString(cursor, EMOJI)
          )
        }
      }

    return entries
      .sortedWith { lhs, rhs ->
        similarityScore(query, lhs.label) - similarityScore(query, rhs.label)
      }
      .distinctBy { it.emoji }
      .take(originalLimit)
      .map { it.emoji }
  }

  /**
   * Deletes the content of the current search index and replaces it with the new one.
   */
  fun setSearchIndex(searchIndex: List<EmojiSearchData>) {
    writableDatabase.beginTransaction()
    writableDatabase.delete(TABLE_NAME, null, null)

    for (searchData in searchIndex) {
      for (label in searchData.tags) {
        val values = contentValuesOf(
          LABEL to label,
          EMOJI to searchData.emoji
        )
        writableDatabase.insert(TABLE_NAME, null, values)
      }
    }
    writableDatabase.setTransactionSuccessful()
    writableDatabase.endTransaction()
  }

  /**
   * Ranks how "similar" a match is to the original search term.
   * A lower score means more similar, with 0 being a perfect match.
   *
   * We know that the `searchTerm` must be a substring of the `match`.
   * We determine similarity by how many letters appear before or after the `searchTerm` in the `match`.
   * We give letters that come before the term a bigger weight than those that come after as a way to prefer matches that are prefixed by the `searchTerm`.
   */
  private fun similarityScore(searchTerm: String, match: String): Int {
    if (searchTerm == match) {
      return 0
    }

    val startIndex = match.indexOf(searchTerm)

    val prefixCount = startIndex
    val suffixCount = match.length - (startIndex + searchTerm.length)

    val prefixRankWeight = 1.5f
    val suffixRankWeight = 1f

    return ((prefixCount * prefixRankWeight) + (suffixCount * suffixRankWeight)).roundToInt()
  }

  private data class Entry(val label: String, val emoji: String)
}
