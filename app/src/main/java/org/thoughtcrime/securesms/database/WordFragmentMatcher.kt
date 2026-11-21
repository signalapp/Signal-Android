/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

/**
 * Matches a query against a label by word fragments, like an IDE matches class names.
 * The query is cut into fragments, and every fragment has to be the beginning of a word in the label, in order.
 * "raha" matches "raised hand" as "ra" + "ha". "rsha" does not match, because no word begins with "s".
 * A space in the query always starts a new fragment.
 */
object WordFragmentMatcher {

  private val WHITESPACE = Regex("\\s+")
  private val MARKS = setOf(CharCategory.NON_SPACING_MARK, CharCategory.COMBINING_SPACING_MARK, CharCategory.ENCLOSING_MARK)

  /**
   * @param prefixCount Characters of the label before the first fragment.
   * @param unmatchedCount Characters of the label from the first fragment on that the query does not cover.
   */
  data class Match(val fragmentCount: Int, val prefixCount: Int, val unmatchedCount: Int)

  /**
   * @return One match for every word of the label the query can start at, each with the fewest fragments possible from there.
   */
  fun matches(query: String, label: String): List<Match> {
    val search = Search(queryWords = query.lowercase().split(WHITESPACE).filter { it.isNotEmpty() }, label = label.lowercase())

    return search.wordStarts.mapNotNull { start -> search.matchFrom(start) }
  }

  private fun Char.isWordCharacter(): Boolean {
    return isLetterOrDigit() || category in MARKS
  }

  private class Search(queryWords: List<String>, private val label: String) {

    private val query: String = queryWords.joinToString(separator = "")
    private val queryWordStarts: Set<Int> = queryWords.runningFold(0) { start, word -> start + word.length }.toSet()

    val wordStarts: List<Int> = label.indices.filter { index ->
      label[index].isWordCharacter() && (index == 0 || !label[index - 1].isWordCharacter())
    }

    fun matchFrom(start: Int): Match? {
      return fewestFragments(queryIndex = 0, labelIndex = start)?.let { fragmentCount ->
        Match(fragmentCount = fragmentCount, prefixCount = start, unmatchedCount = label.length - start - query.length)
      }
    }

    private fun fewestFragments(queryIndex: Int, labelIndex: Int): Int? {
      val longestFragment = (0 until minOf(query.length - queryIndex, label.length - labelIndex))
        .takeWhile { offset -> query[queryIndex + offset] == label[labelIndex + offset] && (offset == 0 || queryIndex + offset !in queryWordStarts) }
        .count()

      return (1..longestFragment)
        .mapNotNull { length -> fewestFragmentsAfter(queryIndex = queryIndex + length, labelIndex = labelIndex + length) }
        .minOrNull()
    }

    private fun fewestFragmentsAfter(queryIndex: Int, labelIndex: Int): Int? {
      if (queryIndex == query.length) {
        return 1
      }

      return wordStarts
        .filter { nextStart -> nextStart >= labelIndex }
        .mapNotNull { nextStart -> fewestFragments(queryIndex = queryIndex, labelIndex = nextStart) }
        .minOrNull()
        ?.plus(1)
    }
  }
}
