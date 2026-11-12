/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediaoverview.v2

import androidx.paging.PagingSource
import androidx.paging.PagingState
import org.signal.core.util.Stopwatch
import org.signal.core.util.logging.Log
import org.signal.mediaoverview.MediaOverviewItem
import org.signal.mediaoverview.MediaOverviewState
import org.thoughtcrime.securesms.database.MediaTable

/**
 * Pages one tab's media by offset. Any attachment change invalidates the source, so the pager
 * reloads around wherever the user is.
 *
 * A single thread is counted up front so the pager can show placeholders. All threads are not counted,
 * since that means joining every attachment to its message, so those pages just append as the user scrolls.
 */
class MediaOverviewPagingSource(
  private val repository: MediaOverviewRepository,
  val threadId: Long,
  val tab: MediaOverviewState.Tab,
  val sorting: MediaOverviewState.Sorting
) : PagingSource<Int, MediaOverviewItem>() {

  companion object {
    private val TAG = Log.tag(MediaOverviewPagingSource::class)
  }

  private val isCounted = threadId != MediaTable.ALL_THREADS.toLong()

  /** Safe to hold, since any change that could alter it also invalidates this source. */
  @Volatile
  private var totalCount: Int? = null

  init {
    registerInvalidatedCallback(repository.observeMediaChanges { invalidate() })
  }

  override val jumpingSupported: Boolean = true

  override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaOverviewItem> {
    val stopwatch = Stopwatch("load", decimalPlaces = 1)

    val total = if (isCounted) {
      totalCount ?: repository.getCount(threadId, tab).also {
        totalCount = it
        stopwatch.split("count")
      }
    } else {
      null
    }
    val key = params.key ?: 0

    val offset = when (params) {
      is LoadParams.Prepend -> (key - params.loadSize).coerceAtLeast(0)
      is LoadParams.Append -> key
      is LoadParams.Refresh -> if (total != null) key.coerceAtMost((total - params.loadSize).coerceAtLeast(0)) else key
    }
    val limit = if (params is LoadParams.Prepend) key - offset else params.loadSize

    val records = repository.getPageRecords(threadId, tab, sorting, offset, limit)
    stopwatch.split("query")

    val recipients = repository.resolveRecipients(records)
    stopwatch.split("recipients")

    val items = repository.toItems(records, recipients)
    stopwatch.split("items")

    val end = offset + items.size
    Log.d(TAG, "${params.typeName()} $tab $sorting offset=$offset limit=$limit rows=${items.size} recipients=${recipients.size} total=$total ${stopwatch.stopAndGetLogString()}")

    val prevKey = if (offset > 0) offset else null
    return if (total != null) {
      LoadResult.Page(
        data = items,
        prevKey = prevKey,
        nextKey = if (items.size == limit && end < total) end else null,
        itemsBefore = offset,
        itemsAfter = (total - end).coerceAtLeast(0)
      )
    } else {
      LoadResult.Page(
        data = items,
        prevKey = prevKey,
        nextKey = if (items.size == limit) end else null
      )
    }
  }

  /** Without placeholders the anchor counts from the first loaded page, so it is shifted by that page's offset. */
  override fun getRefreshKey(state: PagingState<Int, MediaOverviewItem>): Int? {
    val anchor = state.anchorPosition ?: return null
    val position = if (isCounted) anchor else anchor + (state.pages.firstOrNull()?.prevKey ?: 0)
    return (position - state.config.initialLoadSize / 2).coerceAtLeast(0)
  }

  private fun LoadParams<Int>.typeName(): String {
    return when (this) {
      is LoadParams.Refresh -> "refresh"
      is LoadParams.Append -> "append"
      is LoadParams.Prepend -> "prepend"
    }
  }
}
