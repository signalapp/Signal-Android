/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediaoverview.v2

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.Test
import org.signal.core.models.database.AttachmentId
import org.signal.mediaoverview.MediaOverviewItem
import org.signal.mediaoverview.MediaOverviewRow
import org.signal.mediaoverview.MediaOverviewState.SectionHeader
import org.signal.mediaoverview.MediaOverviewState.Sorting
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class MediaOverviewSectionsTest {

  private val today = LocalDate.of(2026, 11, 2)
  private val zone = ZoneOffset.UTC

  @Test
  fun `dates fall into recency buckets, then calendar months`() {
    val sections = MediaOverviewSections(Sorting.NEWEST, today, zone)

    val headers = listOf(-0.5, 0.5, 3.0, 20.0, 60.0, 400.0).map { sections.headerFor(item(1, date = daysAgo(it))) }

    assertThat(headers).containsExactly(
      SectionHeader.Today,
      SectionHeader.Yesterday,
      SectionHeader.ThisWeek,
      SectionHeader.ThisMonth,
      SectionHeader.Month(YearMonth.of(2026, 9)),
      SectionHeader.Month(YearMonth.of(2025, 9))
    )
  }

  @Test
  fun `sorting by size buckets by rough size instead of date`() {
    val sections = MediaOverviewSections(Sorting.LARGEST, today, zone)

    val headers = listOf(30L * 1024 * 1024, 5L * 1024 * 1024, 10L).map { sections.headerFor(item(1, size = it)) }

    assertThat(headers).containsExactly(SectionHeader.Large, SectionHeader.Medium, SectionHeader.Small)
  }

  @Test
  fun `the first item gets a header keyed by that item`() {
    val first = item(1, date = daysAgo(-0.5))

    val separator = MediaOverviewSections(Sorting.NEWEST, today, zone).separator(before = null, after = first)

    assertThat(separator).isEqualTo(MediaOverviewRow.Header(SectionHeader.Today, first.key))
  }

  @Test
  fun `neighbours in the same section get no header between them`() {
    val separator = MediaOverviewSections(Sorting.NEWEST, today, zone).separator(item(1, date = daysAgo(-0.5)), item(2, date = daysAgo(-0.25)))

    assertThat(separator).isNull()
  }

  @Test
  fun `a new section starts with a header for its first item`() {
    val older = item(2, date = daysAgo(60.0))

    val separator = MediaOverviewSections(Sorting.NEWEST, today, zone).separator(item(1, date = daysAgo(-0.5)), older)

    assertThat(separator).isEqualTo(MediaOverviewRow.Header(SectionHeader.Month(YearMonth.of(2026, 9)), older.key))
  }

  @Test
  fun `nothing goes after the last item`() {
    assertThat(MediaOverviewSections(Sorting.NEWEST, today, zone).separator(item(1), after = null)).isNull()
  }

  private fun daysAgo(days: Double): Long {
    return today.atStartOfDay(zone).toInstant().toEpochMilli() - (days * 24 * 60 * 60 * 1000).toLong()
  }

  private fun item(id: Long, date: Long = 0, size: Long = 0): MediaOverviewItem {
    return MediaOverviewItem(
      key = MediaOverviewItem.Key.Attachment(AttachmentId(id)),
      kind = MediaOverviewItem.Kind.IMAGE,
      messageId = id,
      threadId = 1,
      date = date,
      formattedDate = "",
      size = size
    )
  }
}
