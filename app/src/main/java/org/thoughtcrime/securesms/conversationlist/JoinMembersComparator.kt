package org.thoughtcrime.securesms.conversationlist

/**
 * Compares two display names, preferring the one which includes the given [highlightSubstring].
 */
class JoinMembersComparator(private val highlightSubstring: String) : Comparator<String> {
  override fun compare(o1: String, o2: String): Int {
    val o1ContainsSubstring = o1.contains(highlightSubstring, true)
    val o2ContainsSubstring = o2.contains(highlightSubstring, true)
    return if (!(o1ContainsSubstring xor o2ContainsSubstring)) {
      o1.compareTo(o2)
    } else if (o1ContainsSubstring) {
      -1
    } else {
      1
    }
  }
}
