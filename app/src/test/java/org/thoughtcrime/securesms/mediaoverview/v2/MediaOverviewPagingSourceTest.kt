/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediaoverview.v2

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingSource.LoadResult
import androidx.paging.PagingState
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.signal.core.models.database.AttachmentId
import org.signal.mediaoverview.MediaOverviewItem
import org.signal.mediaoverview.MediaOverviewState.Sorting
import org.signal.mediaoverview.MediaOverviewState.Tab
import org.thoughtcrime.securesms.database.MediaTable

class MediaOverviewPagingSourceTest {

  companion object {
    private const val THREAD_ID = 7L
    private const val TOTAL = 250
    private val ALL_THREADS = MediaTable.ALL_THREADS.toLong()
  }

  private val repository = mockk<MediaOverviewRepository>()
  private val onChange = slot<() -> Unit>()
  private var unregistered = false

  @Before
  fun setUp() {
    every { repository.observeMediaChanges(capture(onChange)) } returns { unregistered = true }
    coEvery { repository.getCount(THREAD_ID, Tab.MEDIA) } returns TOTAL
    coEvery { repository.getPageRecords(THREAD_ID, Tab.MEDIA, Sorting.NEWEST, any(), any()) } answers {
      val offset = arg<Int>(3)
      val limit = arg<Int>(4)
      (offset until minOf(offset + limit, TOTAL)).map { record(it.toLong()) }
    }
    coEvery { repository.resolveRecipients(any()) } returns emptyMap()
    coEvery { repository.toItems(any(), any()) } answers { firstArg<List<MediaTable.MediaRecord>>().map { item(it.messageId) } }
  }

  @Test
  fun `the first page starts at the top and counts the rest as placeholders`() = runTest {
    val page = createSource().load(PagingSource.LoadParams.Refresh(key = null, loadSize = 60, placeholdersEnabled = true)).asPage()

    assertThat(page.data.first().messageId).isEqualTo(0L)
    assertThat(page.data.size).isEqualTo(60)
    assertThat(page.prevKey).isNull()
    assertThat(page.nextKey).isEqualTo(60)
    assertThat(page.itemsBefore).isEqualTo(0)
    assertThat(page.itemsAfter).isEqualTo(190)
  }

  @Test
  fun `appending continues where the last page ended and stops at the end`() = runTest {
    val page = createSource().load(PagingSource.LoadParams.Append(key = 200, loadSize = 60, placeholdersEnabled = true)).asPage()

    assertThat(page.data.size).isEqualTo(50)
    assertThat(page.data.first().messageId).isEqualTo(200L)
    assertThat(page.nextKey).isNull()
    assertThat(page.itemsAfter).isEqualTo(0)
  }

  @Test
  fun `prepending loads the items just before the key, without running past the start`() = runTest {
    val page = createSource().load(PagingSource.LoadParams.Prepend(key = 40, loadSize = 60, placeholdersEnabled = true)).asPage()

    assertThat(page.data.size).isEqualTo(40)
    assertThat(page.data.first().messageId).isEqualTo(0L)
    assertThat(page.data.last().messageId).isEqualTo(39L)
    assertThat(page.prevKey).isNull()
  }

  @Test
  fun `a refresh past the end is pulled back so the page is full`() = runTest {
    val page = createSource().load(PagingSource.LoadParams.Refresh(key = 240, loadSize = 60, placeholdersEnabled = true)).asPage()

    assertThat(page.itemsBefore).isEqualTo(190)
    assertThat(page.data.size).isEqualTo(60)
    assertThat(page.prevKey).isEqualTo(190)
  }

  @Test
  fun `refreshing reloads around the anchor`() {
    val state = PagingState<Int, MediaOverviewItem>(
      pages = emptyList(),
      anchorPosition = 150,
      config = PagingConfig(pageSize = 60),
      leadingPlaceholderCount = 0
    )

    assertThat(createSource().getRefreshKey(state)).isEqualTo(60)
  }

  @Test
  fun `a media change invalidates the source and stops observing`() {
    val source = createSource()
    assertThat(source.invalid).isFalse()

    onChange.captured()

    assertThat(source.invalid).isTrue()
    assertThat(unregistered).isTrue()
  }

  @Test
  fun `the count is only read once per source`() = runTest {
    val source = createSource()

    source.load(PagingSource.LoadParams.Refresh(key = null, loadSize = 60, placeholdersEnabled = true))
    source.load(PagingSource.LoadParams.Append(key = 60, loadSize = 60, placeholdersEnabled = true))

    coVerify(exactly = 1) { repository.getCount(THREAD_ID, Tab.MEDIA) }
  }

  @Test
  fun `across all threads nothing is counted and pages append until one comes back short`() = runTest {
    coEvery { repository.getPageRecords(ALL_THREADS, Tab.MEDIA, Sorting.LARGEST, any(), any()) } answers {
      val offset = arg<Int>(3)
      val limit = arg<Int>(4)
      (offset until minOf(offset + limit, TOTAL)).map { record(it.toLong()) }
    }
    val source = MediaOverviewPagingSource(repository, ALL_THREADS, Tab.MEDIA, Sorting.LARGEST)

    val first = source.load(PagingSource.LoadParams.Refresh(key = null, loadSize = 60, placeholdersEnabled = true)).asPage()
    val last = source.load(PagingSource.LoadParams.Append(key = 240, loadSize = 60, placeholdersEnabled = true)).asPage()

    assertThat(first.nextKey).isEqualTo(60)
    assertThat(first.itemsBefore).isEqualTo(LoadResult.Page.COUNT_UNDEFINED)
    assertThat(first.itemsAfter).isEqualTo(LoadResult.Page.COUNT_UNDEFINED)
    assertThat(last.data.size).isEqualTo(10)
    assertThat(last.nextKey).isNull()
    coVerify(exactly = 0) { repository.getCount(any(), any()) }
  }

  @Test
  fun `across all threads refreshing reloads around the anchor counted from the first loaded page`() {
    val page = LoadResult.Page(data = List(60) { item(it.toLong()) }, prevKey = 300, nextKey = 360)
    val state = PagingState(
      pages = listOf(page),
      anchorPosition = 50,
      config = PagingConfig(pageSize = 60),
      leadingPlaceholderCount = 0
    )

    assertThat(MediaOverviewPagingSource(repository, ALL_THREADS, Tab.MEDIA, Sorting.LARGEST).getRefreshKey(state)).isEqualTo(260)
  }

  private fun createSource(): MediaOverviewPagingSource {
    return MediaOverviewPagingSource(repository, THREAD_ID, Tab.MEDIA, Sorting.NEWEST)
  }

  private fun LoadResult<Int, MediaOverviewItem>.asPage(): LoadResult.Page<Int, MediaOverviewItem> {
    assertThat(this).isInstanceOf<LoadResult.Page<Int, MediaOverviewItem>>()
    return this as LoadResult.Page<Int, MediaOverviewItem>
  }

  private fun record(id: Long): MediaTable.MediaRecord {
    return mockk { every { messageId } returns id }
  }

  private fun item(id: Long): MediaOverviewItem {
    return MediaOverviewItem(
      key = MediaOverviewItem.Key.Attachment(AttachmentId(id)),
      kind = MediaOverviewItem.Kind.IMAGE,
      messageId = id,
      threadId = THREAD_ID,
      date = 0,
      formattedDate = "",
      size = 0
    )
  }
}
