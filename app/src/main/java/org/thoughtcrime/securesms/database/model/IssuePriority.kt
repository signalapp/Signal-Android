package org.thoughtcrime.securesms.database.model

/**
 * The relative importance of a recorded issue. Stored in the database as [value] so that ordering and threshold
 * comparisons are meaningful.
 */
enum class IssuePriority(val value: Int, val label: String) {
  LOW(100, "Low"),
  MEDIUM(200, "Medium"),
  HIGH(300, "High");

  companion object {
    fun fromValue(value: Int): IssuePriority {
      return entries.firstOrNull { it.value == value } ?: LOW
    }
  }
}
