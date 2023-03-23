package org.signal.core.util

/**
 * Treats the string as a serialized list of tokens and tells you if an item is present in the list.
 * In addition to exact matches, this handles wildcards at the end of an item.
 *
 * e.g. a,b,c*,d
 */
fun String.asListContains(item: String): Boolean {
  val items: List<String> = this
    .split(",")
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .toList()

  val exactMatches = items.filter { it.last() != '*' }
  val prefixMatches = items.filter { it.last() == '*' }

  return exactMatches.contains(item) ||
    prefixMatches
      .map { it.substring(0, it.length - 1) }
      .any { item.startsWith(it) }
}

/**
 * Turns a multi-line string into a single-line string stripped of indentation, separated by spaces instead of newlines.
 *
 * e.g.
 *
 * a
 *   b
 * c
 *
 * turns into
 *
 * a b c
 */
fun String.toSingleLine(): String {
  return this.trimIndent().split("\n").joinToString(separator = " ")
}

fun String?.emptyIfNull(): String {
  return this ?: ""
}
