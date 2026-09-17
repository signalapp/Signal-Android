/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.gif

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    repository = mockk()
    coEvery { repository.getGifs("", 0, any()) } returns Result.success(GifPage(gifs(20), hasMore = true))
    actions = mutableListOf()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun createViewModel(): GifPageViewModel {
    val viewModel = GifPageViewModel(repository, actions::add)
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
