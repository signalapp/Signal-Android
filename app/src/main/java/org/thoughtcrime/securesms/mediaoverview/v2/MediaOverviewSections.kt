/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediaoverview.v2

import androidx.paging.PagingData
import androidx.paging.insertSeparators
import androidx.paging.map
import org.signal.core.util.mebiBytes
import org.signal.mediaoverview.MediaOverviewItem
import org.signal.mediaoverview.MediaOverviewRow
import org.signal.mediaoverview.MediaOverviewState.SectionHeader
import org.signal.mediaoverview.MediaOverviewState.Sorting
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Decides which section each item falls in: by date, or by rough size when sorting by size.
 *
 * @param zone The zone that item dates are bucketed into days with.
 */
class MediaOverviewSections(
  private val sorting: Sorting,
  private val today: LocalDate,
  private val zone: ZoneId = ZoneId.systemDefault()
) {

  fun headerFor(item: MediaOverviewItem): SectionHeader {
    return if (sorting == Sorting.LARGEST) sizeHeader(item.size) else dateHeader(item.date)
  }

  /** The header to put between two neighbouring items, if [after] starts a new section. */
  fun separator(before: MediaOverviewItem?, after: MediaOverviewItem?): MediaOverviewRow.Header? {
    if (after == null) {
      return null
    }

    val header = headerFor(after)
    return if (before == null || headerFor(before) != header) MediaOverviewRow.Header(header, after.key) else null
  }

  private fun sizeHeader(size: Long): SectionHeader {
    return when {
      size < 1.mebiBytes.inWholeBytes -> SectionHeader.Small
      size < 20.mebiBytes.inWholeBytes -> SectionHeader.Medium
      else -> SectionHeader.Large
    }
  }

  private fun dateHeader(date: Long): SectionHeader {
    val day = Instant.ofEpochMilli(date).atZone(zone).toLocalDate()

    return when {
      day >= today -> SectionHeader.Today
      day >= today.minusDays(1) -> SectionHeader.Yesterday
      day >= today.minusDays(6) -> SectionHeader.ThisWeek
      day >= today.minusDays(30) -> SectionHeader.ThisMonth
      else -> SectionHeader.Month(YearMonth.from(day))
    }
  }
}

fun PagingData<MediaOverviewItem>.withSectionHeaders(sections: MediaOverviewSections): PagingData<MediaOverviewRow> {
  return map<MediaOverviewItem, MediaOverviewRow> { MediaOverviewRow.Media(it) }
    .insertSeparators { before, after ->
      sections.separator((before as? MediaOverviewRow.Media)?.item, (after as? MediaOverviewRow.Media)?.item)
    }
}
