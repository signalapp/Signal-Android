/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare.screens.selectcontact

import androidx.paging.PagingSource
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.thoughtcrime.securesms.contacts.index.ContactIndexRecord
import org.thoughtcrime.securesms.contacts.index.ContactIndexType

@OptIn(ExperimentalCoroutinesApi::class)
class ContactIndexPagingSourceTest {

  @Test
  fun `refresh starts at the first position`() = runTest {
    val source = FakeSource(rows(1, 5))
    val pagingSource = ContactIndexPagingSource(source.mock, query = "")

    val page = pagingSource.load(refresh(loadSize = 3)) as PagingSource.LoadResult.Page

    assertThat(page.data.map { it.position }).containsExactly(1L, 2L, 3L)
    assertThat(source.requestedStarts).containsExactly(ContactIndexPagingSource.FIRST_POSITION)
  }

  @Test
  fun `a full page points at the next position`() = runTest {
    val pagingSource = ContactIndexPagingSource(FakeSource(rows(1, 10)).mock, query = "")

    val page = pagingSource.load(refresh(loadSize = 3)) as PagingSource.LoadResult.Page

    assertThat(page.nextKey).isEqualTo(4L)
  }

  @Test
  fun `a short page is the end of the list`() = runTest {
    val pagingSource = ContactIndexPagingSource(FakeSource(rows(1, 2)).mock, query = "")

    val page = pagingSource.load(refresh(loadSize = 10)) as PagingSource.LoadResult.Page

    assertThat(page.nextKey).isNull()
  }

  @Test
  fun `the next key comes from the row, so a sparse filter still advances`() = runTest {
    // A filtered query matches rows scattered through the index, so the next key cannot be derived
    // from the start position plus the page size.
    val scattered = listOf(rowAt(4), rowAt(9), rowAt(30))
    val pagingSource = ContactIndexPagingSource(FakeSource(scattered).mock, query = "anna")

    val page = pagingSource.load(refresh(loadSize = 3)) as PagingSource.LoadResult.Page

    assertThat(page.nextKey).isEqualTo(31L)
  }

  @Test
  fun `the first page cannot be paged backwards`() = runTest {
    val pagingSource = ContactIndexPagingSource(FakeSource(rows(1, 10)).mock, query = "")

    val page = pagingSource.load(refresh(loadSize = 3)) as PagingSource.LoadResult.Page

    assertThat(page.prevKey).isNull()
  }

  @Test
  fun `a later page can be paged backwards, so dropped windows can be reloaded`() = runTest {
    val pagingSource = ContactIndexPagingSource(FakeSource(rows(1, 400)).mock, query = "")

    val page = pagingSource.load(
      PagingSource.LoadParams.Append(key = 201L, loadSize = 100, placeholdersEnabled = false)
    ) as PagingSource.LoadResult.Page

    assertThat(page.prevKey).isEqualTo(101L)
  }

  @Test
  fun `paging backwards never runs off the front of the index`() = runTest {
    val pagingSource = ContactIndexPagingSource(FakeSource(rows(1, 400)).mock, query = "")

    val page = pagingSource.load(
      PagingSource.LoadParams.Append(key = 20L, loadSize = 100, placeholdersEnabled = false)
    ) as PagingSource.LoadResult.Page

    assertThat(page.prevKey).isEqualTo(ContactIndexPagingSource.FIRST_POSITION)
  }

  @Test
  fun `an unfiltered page reports what surrounds it, so the list is its full length`() = runTest {
    val pagingSource = ContactIndexPagingSource(FakeSource(rows(1, 500)).mock, query = "")

    val page = pagingSource.load(
      PagingSource.LoadParams.Append(key = 201L, loadSize = 100, placeholdersEnabled = true)
    ) as PagingSource.LoadResult.Page

    assertThat(page.itemsBefore).isEqualTo(200)
    assertThat(page.itemsAfter).isEqualTo(200)
  }

  /** Placeholders only line up when the three add back up to the whole index. */
  @Test
  fun `the counts either side of the last page account for the whole index`() = runTest {
    val pagingSource = ContactIndexPagingSource(FakeSource(rows(1, 250)).mock, query = "")

    val page = pagingSource.load(
      PagingSource.LoadParams.Append(key = 201L, loadSize = 100, placeholdersEnabled = true)
    ) as PagingSource.LoadResult.Page

    assertThat(page.itemsBefore).isEqualTo(200)
    assertThat(page.data).hasSize(50)
    assertThat(page.itemsAfter).isEqualTo(0)
  }

  /**
   * Matches are scattered through the index, so a filtered page cannot say how many rows surround it
   * without scanning the rest. An undefined count is how it declines to offer placeholders.
   */
  @Test
  fun `a filtered page reports no counts`() = runTest {
    val source = FakeSource(rows(1, 500))
    val pagingSource = ContactIndexPagingSource(source.mock, query = "anna")

    val page = pagingSource.load(refresh(loadSize = 3)) as PagingSource.LoadResult.Page

    assertThat(page.itemsBefore).isEqualTo(PagingSource.LoadResult.Page.COUNT_UNDEFINED)
    assertThat(page.itemsAfter).isEqualTo(PagingSource.LoadResult.Page.COUNT_UNDEFINED)
    assertThat(source.countCalls).isEqualTo(0)
  }

  @Test
  fun `the index is counted once however many pages are read`() = runTest {
    val source = FakeSource(rows(1, 500))
    val pagingSource = ContactIndexPagingSource(source.mock, query = "")

    pagingSource.load(refresh(loadSize = 100))
    pagingSource.load(PagingSource.LoadParams.Append(key = 101L, loadSize = 100, placeholdersEnabled = true))
    pagingSource.load(PagingSource.LoadParams.Append(key = 201L, loadSize = 100, placeholdersEnabled = true))

    assertThat(source.countCalls).isEqualTo(1)
  }

  @Test
  fun `a failure to read is surfaced rather than thrown`() = runTest {
    val pagingSource = ContactIndexPagingSource(throwingSource(), query = "")

    val result = pagingSource.load(refresh(loadSize = 3))

    assertThat(result is PagingSource.LoadResult.Error).isEqualTo(true)
  }

  private fun refresh(loadSize: Int) = PagingSource.LoadParams.Refresh<Long>(
    key = null,
    loadSize = loadSize,
    placeholdersEnabled = false
  )

  private fun rows(from: Long, to: Long): List<ContactIndexRecord> = (from..to).map { rowAt(it) }

  private fun rowAt(position: Long): ContactIndexRecord {
    return ContactIndexRecord(
      position = position,
      type = ContactIndexType.SYSTEM_ONLY,
      section = "A",
      displayName = "Contact $position",
      recipientId = null,
      lookupKey = "lookup-$position",
      contactId = position,
      hasPersonalName = true,
      hasPhoto = false
    )
  }

  /** Serves [rows] as a dense index, recording the start position of every page it is asked for. */
  private class FakeSource(private val rows: List<ContactIndexRecord>) {
    val requestedStarts = mutableListOf<Long>()
    var countCalls = 0

    val mock: ContactIndexSource = mockk(relaxUnitFun = true)

    init {
      coEvery { mock.count() } answers {
        countCalls++
        rows.size
      }
      coEvery { mock.page(any(), any(), any()) } answers {
        val startPosition = secondArg<Long>()
        val limit = thirdArg<Int>()

        requestedStarts += startPosition
        rows.filter { it.position >= startPosition }.take(limit)
      }
    }
  }

  private fun throwingSource(): ContactIndexSource {
    return mockk(relaxUnitFun = true) {
      coEvery { count() } returns 0
      coEvery { page(any(), any(), any()) } throws IllegalStateException("boom")
    }
  }
}
