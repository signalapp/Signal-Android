/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.signal.mediakeyboard.data.MediaKeyboardRepository
import org.signal.remoteconfig.FakeRemoteConfigDependencies
import org.signal.remoteconfig.RemoteConfig

@OptIn(ExperimentalCoroutinesApi::class)
class MediaKeyboardViewModelTest {

  private val testDispatcher = StandardTestDispatcher()

  private lateinit var repository: MediaKeyboardRepository
  private lateinit var isEnteringText: MutableStateFlow<Boolean>

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    repository = mockk()
    isEnteringText = MutableStateFlow(false)
    RemoteConfig.initDependencies(FakeRemoteConfigDependencies())
    mockkObject(RemoteConfig)
  }

  @After
  fun tearDown() {
    unmockkObject(RemoteConfig)
    Dispatchers.resetMain()
  }

  private fun createViewModel(gifSearchAvailable: Boolean = true): MediaKeyboardViewModel {
    every { RemoteConfig.gifSearchAvailable } returns gifSearchAvailable
    val viewModel = MediaKeyboardViewModel(repository, isEnteringText)
    testDispatcher.scheduler.advanceUntilIdle()
    return viewModel
  }

  @Test
  fun `initialize - populates available tabs`() {
    val viewModel = createViewModel()

    assertThat(viewModel.state.value.availableTabs).isEqualTo(MediaKeyboardTab.entries.toList())
    assertThat(viewModel.state.value.selectedTab).isEqualTo(MediaKeyboardTab.EMOJI)
    assertThat(viewModel.state.value.initialized).isTrue()
  }

  @Test
  fun `initialize - hides the gif tab when gif search is unavailable`() {
    val viewModel = createViewModel(gifSearchAvailable = false)

    assertThat(viewModel.state.value.availableTabs).isEqualTo(listOf(MediaKeyboardTab.EMOJI, MediaKeyboardTab.STICKER))
  }

  @Test
  fun `restrict tabs - narrows to the given tabs and selects one of them`() {
    val viewModel = createViewModel()
    viewModel.onEvent(MediaKeyboardScreenEvents.TabSelected(MediaKeyboardTab.GIF))
    testDispatcher.scheduler.advanceUntilIdle()

    viewModel.restrictTabs(setOf(MediaKeyboardTab.EMOJI))

    assertThat(viewModel.state.value.availableTabs).isEqualTo(listOf(MediaKeyboardTab.EMOJI))
    assertThat(viewModel.state.value.selectedTab).isEqualTo(MediaKeyboardTab.EMOJI)
  }

  @Test
  fun `restrict tabs - lifting the restriction returns to the tab the user had picked`() {
    val viewModel = createViewModel()
    viewModel.onEvent(MediaKeyboardScreenEvents.TabSelected(MediaKeyboardTab.GIF))
    testDispatcher.scheduler.advanceUntilIdle()

    viewModel.restrictTabs(setOf(MediaKeyboardTab.EMOJI))
    viewModel.restrictTabs(null)

    assertThat(viewModel.state.value.availableTabs).isEqualTo(MediaKeyboardTab.entries.toList())
    assertThat(viewModel.state.value.selectedTab).isEqualTo(MediaKeyboardTab.GIF)
  }

  @Test
  fun `restrict tabs - never offers a tab that is unavailable`() {
    val viewModel = createViewModel(gifSearchAvailable = false)

    viewModel.restrictTabs(setOf(MediaKeyboardTab.EMOJI, MediaKeyboardTab.GIF))

    assertThat(viewModel.state.value.availableTabs).isEqualTo(listOf(MediaKeyboardTab.EMOJI))
  }

  @Test
  fun `tab selected - switches tab and clears the query`() {
    val viewModel = createViewModel()

    isEnteringText.value = true
    testDispatcher.scheduler.advanceUntilIdle()

    viewModel.onEvent(MediaKeyboardScreenEvents.SearchQueryChanged("cats"))
    viewModel.onEvent(MediaKeyboardScreenEvents.TabSelected(MediaKeyboardTab.GIF))
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.state.value.selectedTab).isEqualTo(MediaKeyboardTab.GIF)
    assertThat(viewModel.state.value.searchQuery).isEqualTo("")
  }

  @Test
  fun `search query - tracked while the scaffold says search is on`() {
    val viewModel = createViewModel()

    isEnteringText.value = true
    testDispatcher.scheduler.advanceUntilIdle()

    viewModel.onEvent(MediaKeyboardScreenEvents.SearchQueryChanged("dog"))
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.state.value.searchActive).isTrue()
    assertThat(viewModel.state.value.searchQuery).isEqualTo("dog")
  }

  @Test
  fun `search active - follows the scaffold rather than any event`() {
    val viewModel = createViewModel()

    assertThat(viewModel.state.value.searchActive).isFalse()

    isEnteringText.value = true
    testDispatcher.scheduler.advanceUntilIdle()
    assertThat(viewModel.state.value.searchActive).isTrue()

    isEnteringText.value = false
    testDispatcher.scheduler.advanceUntilIdle()
    assertThat(viewModel.state.value.searchActive).isFalse()
  }
}
