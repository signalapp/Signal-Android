/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.gif

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.signal.core.util.Result
import org.signal.mediakeyboard.MediaKeyboardAction
import org.signal.mediakeyboard.data.GifFetchError
import org.signal.mediakeyboard.data.GifKeyboardRepository
import org.signal.mediakeyboard.data.GifPage
import org.signal.mediakeyboard.data.KeyboardGif

@OptIn(ExperimentalCoroutinesApi::class)
class GifPageViewModelTest {

  private val testDispatcher = StandardTestDispatcher()

  private lateinit var repository: GifKeyboardRepository
  private lateinit var actions: MutableList<MediaKeyboardAction>
  private lateinit var collectorScope: CoroutineScope

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    repository = mockk()
    coEvery { repository.getGifs("", 0, any()) } returns Result.success(GifPage(gifs(20), hasMore = true))
    actions = mutableListOf()
    collectorScope = CoroutineScope(testDispatcher)
  }

  @After
  fun tearDown() {
    collectorScope.cancel()
    Dispatchers.resetMain()
  }

  private fun createViewModel(): GifPageViewModel {
    val viewModel = GifPageViewModel(repository)
    collectorScope.launch { viewModel.actions.collect { actions += it } }
    testDispatcher.scheduler.advanceUntilIdle()
    return viewModel
  }

  private fun gifs(count: Int, offset: Int = 0): List<KeyboardGif> {
    return List(count) { KeyboardGif(id = "gif-${offset + it}", still = null, mp4PreviewUri = null, width = 100, height = 100) }
  }

  @Test
  fun `initialize - loads trending`() {
    val viewModel = createViewModel()

    assertThat(viewModel.state.value.gifs).hasSize(20)
    assertThat(viewModel.state.value.isLoading).isFalse()
    assertThat(viewModel.state.value.hasMore).isTrue()
  }

  @Test
  fun `load more - appends next page`() {
    coEvery { repository.getGifs("", 20, any()) } returns Result.success(GifPage(gifs(10, offset = 20), hasMore = false))

    val viewModel = createViewModel()
    viewModel.onEvent(GifPageScreenEvents.LoadMoreRequested)
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.state.value.gifs).hasSize(30)
    assertThat(viewModel.state.value.hasMore).isFalse()
  }

  @Test
  fun `initial load failure - sets loadFailed`() {
    coEvery { repository.getGifs("", 0, any()) } returns Result.failure(GifFetchError.Network)

    val viewModel = createViewModel()

    assertThat(viewModel.state.value.loadFailed).isTrue()
    assertThat(viewModel.state.value.isLoading).isFalse()
  }

  @Test
  fun `quick search - reloads with new query`() {
    coEvery { repository.getGifs("love", 0, any()) } returns Result.success(GifPage(gifs(5), hasMore = false))

    val viewModel = createViewModel()
    viewModel.onEvent(GifPageScreenEvents.QuickSearchSelected(GifQuickSearchOption.LOVE))
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.state.value.selectedQuickSearch).isEqualTo(GifQuickSearchOption.LOVE)
    assertThat(viewModel.state.value.gifs).hasSize(5)
  }

  @Test
  fun `search query - takes the place of the quick search`() {
    coEvery { repository.getGifs("cats", 0, any()) } returns Result.success(GifPage(gifs(3), hasMore = false))

    val viewModel = createViewModel()
    viewModel.onEvent(GifPageScreenEvents.SearchQueryChanged("cats"))
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.state.value.searchQuery).isEqualTo("cats")
    assertThat(viewModel.state.value.gifs).hasSize(3)
  }

  @Test
  fun `search query cleared - returns to the quick search`() {
    coEvery { repository.getGifs("cats", 0, any()) } returns Result.success(GifPage(gifs(3), hasMore = false))

    val viewModel = createViewModel()
    viewModel.onEvent(GifPageScreenEvents.SearchQueryChanged("cats"))
    viewModel.onEvent(GifPageScreenEvents.SearchQueryChanged(""))
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.state.value.searchQuery).isEqualTo("")
    assertThat(viewModel.state.value.gifs).hasSize(20)
  }

  @Test
  fun `search query - a newer query cancels the fetch for the one before it`() {
    coEvery { repository.getGifs("ca", 0, any()) } coAnswers {
      delay(1_000)
      Result.success(GifPage(gifs(7), hasMore = false))
    }
    coEvery { repository.getGifs("cats", 0, any()) } returns Result.success(GifPage(gifs(3), hasMore = false))

    val viewModel = createViewModel()
    viewModel.onEvent(GifPageScreenEvents.SearchQueryChanged("ca"))
    testDispatcher.scheduler.runCurrent()
    viewModel.onEvent(GifPageScreenEvents.SearchQueryChanged("cats"))
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.state.value.searchQuery).isEqualTo("cats")
    assertThat(viewModel.state.value.gifs).hasSize(3)
  }

  @Test
  fun `search field - typing is debounced into a single query`() {
    coEvery { repository.getGifs(any(), 0, any()) } returns Result.success(GifPage(gifs(3), hasMore = false))

    val viewModel = createViewModel()
    listOf("c", "ca", "cat", "cats").forEach { text ->
      viewModel.searchFieldState.setTextAndPlaceCursorAtEnd(text)
      Snapshot.sendApplyNotifications()
      testDispatcher.scheduler.advanceTimeBy(100)
    }
    testDispatcher.scheduler.advanceUntilIdle()

    coVerify(exactly = 0) { repository.getGifs("c", any(), any()) }
    coVerify(exactly = 0) { repository.getGifs("ca", any(), any()) }
    coVerify(exactly = 0) { repository.getGifs("cat", any(), any()) }
    coVerify(exactly = 1) { repository.getGifs("cats", 0, any()) }
  }

  @Test
  fun `gif clicked - reports the selection`() {
    val viewModel = createViewModel()
    val gif = viewModel.state.value.gifs.first()
    viewModel.onEvent(GifPageScreenEvents.GifClicked(gif))
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(actions).isEqualTo(listOf(MediaKeyboardAction.GifSelected(gif)))
  }

  @Test
  fun `search clicked - hands off to the host`() {
    val viewModel = createViewModel()
    viewModel.onEvent(GifPageScreenEvents.SearchClicked)
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(actions).isEqualTo(listOf(MediaKeyboardAction.GifSearchClicked))
  }
}
