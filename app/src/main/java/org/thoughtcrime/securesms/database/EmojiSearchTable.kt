package org.thoughtcrime.securesms.database

import android.content.Context
import android.text.TextUtils
import androidx.core.content.contentValuesOf
import org.signal.core.util.readToSingleInt
import org.signal.core.util.requireInt
import org.signal.core.util.requireNonNullString
import org.signal.core.util.select
import org.signal.core.util.withinTransaction
import org.thoughtcrime.securesms.database.model.EmojiSearchData
import kotlin.math.max

/**
 * Contains all info necessary for full-text search of emoji tags.
 */
class EmojiSearchTable(context: Context, databaseHelper: SignalDatabase) : DatabaseTable(context, databaseHelper) {

  companion object {
    const val TABLE_NAME = "emoji_search"
    const val ID = "_id"
    const val LABEL = "label"
    const val EMOJI = "emoji"
    const val RANK = "rank"

    //language=sql
    const val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY,
        $LABEL TEXT NOT NULL,
        $EMOJI TEXT NOT NULL,
        $RANK INTEGER DEFAULT ${Int.MAX_VALUE} 
      )
      """

    val CREATE_INDEXES = arrayOf(
      "CREATE INDEX emoji_search_rank_covering ON $TABLE_NAME ($RANK, $LABEL, $EMOJI)"
    )
  }

  /**
   * @param query A search query. Doesn't need any special formatted -- it'll be sanitized.
   * @return A list of emoji that are related to the search term, ordered by relevance.
   */
  fun query(originalQuery: String, originalLimit: Int): List<String> {
    val query: String = originalQuery.trim()

    if (TextUtils.isEmpty(query)) {
      return emptyList()
    }

    val limit: Int = max(originalLimit, 200)
    val entries = mutableListOf<Entry>()

    val maxRank = readableDatabase
      .select("MAX($RANK) AS max")
      .from(TABLE_NAME)
      .where("$RANK != ${Int.MAX_VALUE}")
      .run()
      .readToSingleInt()

    readableDatabase
      .select(LABEL, EMOJI, RANK)
      .from(TABLE_NAME)
      .where("$LABEL LIKE ?", "%$query%")
      .orderBy("$RANK ASC")
      .limit(limit)
      .run()
      .use { cursor ->
        while (cursor.moveToNext()) {
          entries += Entry(
            label = cursor.requireNonNullString(LABEL),
            emoji = cursor.requireNonNullString(EMOJI),
            rank = cursor.requireInt(RANK)
          )
        }
      }

    return entries
      .sortedWith { lhs, rhs ->
        val result = similarityScore(query, lhs, maxRank) - similarityScore(query, rhs, maxRank)
        when {
          result < 0 -> -1
          result > 0 -> 1
          else -> 0
        }
      }
      .distinctBy { it.emoji }
      .take(originalLimit)
      .map { it.emoji }
  }

  /**
   * Deletes the content of the current search index and replaces it with the new one.
   */
  fun setSearchIndex(searchIndex: List<EmojiSearchData>) {
    val db = databaseHelper.signalReadableDatabase

    db.withinTransaction {
      db.delete(TABLE_NAME, null, null)

      for (searchData in searchIndex) {
        for (label in searchData.tags) {
          val values = contentValuesOf(
            LABEL to label,
            EMOJI to searchData.emoji,
            RANK to if (searchData.rank == 0) Int.MAX_VALUE else searchData.rank
          )
          db.insert(TABLE_NAME, null, values)
        }
      }
    }
  }

  /**
   * Ranks how "similar" a match is to the original search term.
   * A lower score means more similar, with 0 being a perfect match.
   *
   * We know that the `searchTerm` must be a substring of the `match`.
   * We determine similarity by how many letters appear before or after the `searchTerm` in the `match`.
   * We give letters that come before the term a bigger weight than those that come after as a way to prefer matches that are prefixed by the `searchTerm`.
   */
  private fun similarityScore(searchTerm: String, entry: Entry, maxRank: Int): Float {
    val match: String = entry.label

    if (searchTerm == match) {
      return entry.scaledRank(maxRank)
    }

    val startIndex = match.indexOf(searchTerm)

    val prefixCount = startIndex
    val suffixCount = match.length - (startIndex + searchTerm.length)

    val prefixRankWeight = 1.75f
    val suffixRankWeight = 0.75f
    val notExactMatchPenalty = 2f

    return notExactMatchPenalty +
      (prefixCount * prefixRankWeight) +
      (suffixCount * suffixRankWeight) +
      entry.scaledRank(maxRank)
  }

  private data class Entry(val label: String, val emoji: String, val rank: Int) {
    fun scaledRank(maxRank: Int): Float {
      val unranked = 2f
      val scaleFactor: Float = unranked / maxRank
      return if (rank == Int.MAX_VALUE) {
        unranked
      } else {
        rank * scaleFactor
      }
    }
  }
}
