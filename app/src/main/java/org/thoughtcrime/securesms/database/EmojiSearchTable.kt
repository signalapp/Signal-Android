package org.thoughtcrime.securesms.database

import android.content.Context
import android.database.Cursor
import android.text.TextUtils
import androidx.core.content.contentValuesOf
import org.signal.core.database.exists
import org.signal.core.database.select
import org.signal.core.database.withinTransaction
import org.signal.core.util.readToList
import org.signal.core.util.readToSingleInt
import org.signal.core.util.requireInt
import org.signal.core.util.requireNonNullString
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

    private const val NOT_EXACT_MATCH_PENALTY = 2f
    private const val PREFIX_RANK_WEIGHT = 1.75f
    private const val SUFFIX_RANK_WEIGHT = 0.75f
    private const val UNMATCHED_RANK_WEIGHT = 0.25f
    private const val EXTRA_FRAGMENT_PENALTY = 1f
  }

  /**
   * @param query A search query. Doesn't need any special formatted -- it'll be sanitized.
   * @return A list of emoji that are related to the search term, ordered by relevance.
   */
  fun query(originalQuery: String, originalLimit: Int): List<String> {
    return search(originalQuery, originalLimit, withWordFragments = false)
  }

  /**
   * Like [query], but when that finds fewer emoji than the limit, labels that match the query by word fragments are ranked among the results.
   * See [WordFragmentMatcher].
   */
  fun queryWithWordFragments(originalQuery: String, originalLimit: Int): List<String> {
    return search(originalQuery, originalLimit, withWordFragments = true)
  }

  /**
   * Deletes the content of the current search index and replaces it with the new one.
   */
  fun setSearchIndex(
    localizedSearchIndex: List<EmojiSearchData>,
    englishSearchIndex: List<EmojiSearchData>
  ) {
    databaseHelper.signalReadableDatabase.withinTransaction { db ->
      db.delete(TABLE_NAME, null, null)
      db.insert(localizedSearchIndex)
      db.insert(englishSearchIndex)
    }
  }

  fun hasSearchIndexData(): Boolean {
    return readableDatabase.exists(TABLE_NAME).run()
  }

  private fun SQLiteDatabase.insert(searchIndex: List<EmojiSearchData>) {
    for (searchData in searchIndex) {
      for (label in searchData.tags) {
        val values = contentValuesOf(
          LABEL to label,
          EMOJI to searchData.emoji,
          RANK to if (searchData.rank == 0) Int.MAX_VALUE else searchData.rank
        )
        insert(TABLE_NAME, null, values)
      }
    }
  }

  private fun search(originalQuery: String, originalLimit: Int, withWordFragments: Boolean): List<String> {
    val query: String = originalQuery.trim()

    if (TextUtils.isEmpty(query)) {
      return emptyList()
    }

    val limit: Int = max(originalLimit, 200)

    val maxRank = readableDatabase
      .select("MAX($RANK) AS max")
      .from(TABLE_NAME)
      .where("$RANK != ${Int.MAX_VALUE}")
      .run()
      .readToSingleInt()

    val substringScores = readableDatabase
      .select(LABEL, EMOJI, RANK)
      .from(TABLE_NAME)
      .where("$LABEL LIKE ? OR $EMOJI = ?", "%$query%", query)
      .orderBy("$RANK ASC")
      .limit(limit)
      .run()
      .readToList { cursor -> cursor.readEntry() }
      .map { entry -> entry to similarityScore(query, entry, maxRank) }

    val fragmentScores = if (withWordFragments && substringScores.distinctBy { (entry, _) -> entry.emoji }.size < originalLimit) {
      fragmentScores(query, maxRank)
    } else {
      emptyList()
    }

    return (substringScores + fragmentScores)
      .sortedBy { (_, score) -> score }
      .map { (entry, _) -> entry.emoji }
      .distinct()
      .take(originalLimit)
  }

  private fun fragmentScores(query: String, maxRank: Int): List<Pair<Entry, Float>> {
    return readableDatabase
      .select(LABEL, EMOJI, RANK)
      .from(TABLE_NAME)
      .where("$LABEL LIKE ? AND $LABEL NOT LIKE ?", subsequencePattern(query), "%$query%")
      .orderBy("$RANK ASC")
      .run()
      .readToList { cursor -> cursor.readEntry() }
      .mapNotNull { entry ->
        WordFragmentMatcher.matches(query, entry.label)
          .minOfOrNull { match -> fragmentScore(match, entry, maxRank) }
          ?.let { score -> entry to score }
      }
  }

  private fun Cursor.readEntry(): Entry {
    return Entry(
      label = requireNonNullString(LABEL),
      emoji = requireNonNullString(EMOJI),
      rank = requireInt(RANK)
    )
  }

  /**
   * A LIKE pattern for every label that contains the characters of the query in order. [WordFragmentMatcher] narrows those down.
   */
  private fun subsequencePattern(query: String): String {
    return query
      .filterNot { it.isWhitespace() || it.isSurrogate() }
      .toList()
      .joinToString(separator = "%", prefix = "%", postfix = "%")
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

    if (searchTerm == entry.emoji || searchTerm == match) {
      return entry.scaledRank(maxRank)
    }

    val startIndex = match.indexOf(searchTerm)

    val prefixCount = startIndex
    val suffixCount = match.length - (startIndex + searchTerm.length)

    return NOT_EXACT_MATCH_PENALTY +
      (prefixCount * PREFIX_RANK_WEIGHT) +
      (suffixCount * SUFFIX_RANK_WEIGHT) +
      entry.scaledRank(maxRank)
  }

  /**
   * Ranks a word fragment match on the same scale as [similarityScore].
   * The letters a fragment match leaves unmatched weigh less than the letters after a substring, and every additional fragment adds a penalty.
   */
  private fun fragmentScore(match: WordFragmentMatcher.Match, entry: Entry, maxRank: Int): Float {
    return NOT_EXACT_MATCH_PENALTY +
      (match.prefixCount * PREFIX_RANK_WEIGHT) +
      (match.unmatchedCount * UNMATCHED_RANK_WEIGHT) +
      ((match.fragmentCount - 1) * EXTRA_FRAGMENT_PENALTY) +
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
