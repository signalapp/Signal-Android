package org.thoughtcrime.securesms.stories.archive

import androidx.annotation.StringRes
import org.thoughtcrime.securesms.R
import kotlin.time.Duration.Companion.days

enum class StoryArchiveDuration(val durationMs: Long, @StringRes val labelRes: Int) {
  FOREVER(-1L, R.string.StoryArchive__forever),
  ONE_YEAR(365.days.inWholeMilliseconds, R.string.StoryArchive__1_year),
  SIX_MONTHS(182.days.inWholeMilliseconds, R.string.StoryArchive__6_months),
  THIRTY_DAYS(30.days.inWholeMilliseconds, R.string.StoryArchive__30_days);

  fun serialize(): Long = durationMs

  companion object {
    fun deserialize(value: Long): StoryArchiveDuration {
      return entries.firstOrNull { it.durationMs == value } ?: throw IllegalArgumentException("Unknown value: $value")
    }
  }
}
