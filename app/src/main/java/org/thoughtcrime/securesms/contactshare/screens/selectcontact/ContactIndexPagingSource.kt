/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare.screens.selectcontact

import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.CancellationException
import org.thoughtcrime.securesms.contacts.index.ContactIndexRecord

/**
 * Pages the contact index, keyed by index position.
 *
 * Positions double as row ids, so paging in either direction is a range scan rather than an offset
 * walk.
 */
class ContactIndexPagingSource(
  private val source: ContactIndexSource,
  private val query: String
) : PagingSource<Long, ContactIndexRecord>() {

  companion object {
    /** Index positions are one based, matching `row_number()`. */
    const val FIRST_POSITION = 1L
  }

  /** Safe to hold, since a rebuild bumps the generation and replaces every source in the pager. */
  private var totalRows: Int? = null

  override suspend fun load(params: LoadParams<Long>): LoadResult<Long, ContactIndexRecord> {
    val start = params.key ?: FIRST_POSITION

    return try {
      val rows = source.page(query, start, params.loadSize)

      // A filtered count is not known without scanning the whole index, so search offers no placeholders.
      val counted = if (query.isBlank()) countsAround(start, rows.size) else null

      LoadResult.Page(
        data = rows,
        prevKey = if (query.isNotBlank() || start <= FIRST_POSITION) null else (start - params.loadSize).coerceAtLeast(FIRST_POSITION),
        // A filtered query's last match may be anywhere, so the next key comes from the row.
        nextKey = if (rows.size < params.loadSize) null else rows.last().position + 1,
        itemsBefore = counted?.first ?: LoadResult.Page.COUNT_UNDEFINED,
        itemsAfter = counted?.second ?: LoadResult.Page.COUNT_UNDEFINED
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      LoadResult.Error(e)
    }
  }

  /** Exact rather than approximate, since positions are contiguous and one based. */
  private suspend fun countsAround(start: Long, loaded: Int): Pair<Int, Int> {
    val total = totalRows ?: source.count().also { totalRows = it }
    val before = (start - 1).coerceIn(0, total.toLong()).toInt()

    return before to (total - before - loaded).coerceAtLeast(0)
  }

  /** Restarts from the top: the index is rebuilt from scratch, so an anchor would point elsewhere. */
  override fun getRefreshKey(state: PagingState<Long, ContactIndexRecord>): Long? = null
}
