/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.emoji

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.signal.mediakeyboard.MediaKeyboardAction
import org.signal.mediakeyboard.MediaKeyboardState
import org.signal.mediakeyboard.MediaKeyboardTab
import org.signal.mediakeyboard.data.EmojiCategoryPage
import org.signal.mediakeyboard.data.EmojiKeyboardCategory
import org.signal.mediakeyboard.data.EmojiKeyboardRepository
import org.signal.mediakeyboard.data.KeyboardEmoji

@OptIn(ExperimentalCoroutinesApi::class)
class EmojiPageViewModelTest {

  private val testDispatcher = StandardTestDispatcher()

  private val thumbsUp = KeyboardEmoji("👍", listOf("👍", "👍🏻", "👍🏼", "👍🏽", "👍🏾", "👍🏿"))
  private val grin = KeyboardEmoji("😀")

  private lateinit var repository: EmojiKeyboardRepository
  private lateinit var parentState: MutableStateFlow<MediaKeyboardState>
  private lateinit var actions: MutableList<MediaKeyboardAction>

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    repository = mockk(relaxed = true)
    coEvery { repository.getEmojiPages() } returns listOf(EmojiCategoryPage(EmojiKeyboardCategory.PEOPLE, listOf(grin, thumbsUp)))
    coEvery { repository.getRecentEmoji() } returns listOf(KeyboardEmoji("😂"))
    coEvery { repository.getPreferredVariations() } returns emptyMap()

    parentState = MutableStateFlow(MediaKeyboardState(offeredTabs = MediaKeyboardTab.entries, preferredTab = MediaKeyboardTab.EMOJI, initialized = true))
    actions = mutableListOf()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun createViewModel(): EmojiPageViewModel {
    val viewModel = EmojiPageViewModel(repository, parentState, actions::add)
    testDispatcher.scheduler.advanceUntilIdle()
    return viewModel
  }

  @Test
  fun `initialize - recents page first, then categories`() {
    val viewModel = createViewModel()

    val categories = viewModel.state.value.pages.map { it.category }
    assertThat(categories).isEqualTo(listOf(EmojiKeyboardCategory.RECENTS, EmojiKeyboardCategory.PEOPLE))
    assertThat(viewModel.state.value.selectedCategory).isEqualTo(EmojiKeyboardCategory.RECENTS)
  }

  @Test
  fun `initialize - no recents page when empty`() {
    coEvery { repository.getRecentEmoji() } returns emptyList()

    val viewModel = createViewModel()

    val categories = viewModel.state.value.pages.map { it.category }
    assertThat(categories).isEqualTo(listOf(EmojiKeyboardCategory.PEOPLE))
  }

  @Test
  fun `emoji clicked - reports the selection and records usage`() {
    val viewModel = createViewModel()

    viewModel.onEvent(EmojiPageScreenEvents.EmojiClicked(grin))
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(actions).isEqualTo(listOf(MediaKeyboardAction.EmojiSelected("😀")))
    coVerify { repository.onEmojiUsed("😀") }
  }

  @Test
  fun `emoji clicked - uses preferred variation`() {
    coEvery { repository.getPreferredVariations() } returns mapOf("👍" to "👍🏾")

    val viewModel = createViewModel()
    viewModel.onEvent(EmojiPageScreenEvents.EmojiClicked(thumbsUp))
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(actions).isEqualTo(listOf(MediaKeyboardAction.EmojiSelected("👍🏾")))
  }

  @Test
  fun `long press - opens variation selector only for emoji with variations`() {
    val viewModel = createViewModel()

    viewModel.onEvent(EmojiPageScreenEvents.EmojiLongPressed("People:0", grin))
    testDispatcher.scheduler.advanceUntilIdle()
    assertThat(viewModel.state.value.variationSelector).isNull()

    viewModel.onEvent(EmojiPageScreenEvents.EmojiLongPressed("People:1", thumbsUp))
    testDispatcher.scheduler.advanceUntilIdle()
    assertThat(viewModel.state.value.variationSelector).isNotNull()
  }

  @Test
  fun `variation selected - persists preference and selects`() {
    val viewModel = createViewModel()

    viewModel.onEvent(EmojiPageScreenEvents.VariationSelected(thumbsUp, "👍🏿"))
    testDispatcher.scheduler.advanceUntilIdle()

    verify { repository.setPreferredVariation("👍", "👍🏿") }
    assertThat(actions).isEqualTo(listOf(MediaKeyboardAction.EmojiSelected("👍🏿")))
    assertThat(viewModel.state.value.variationSelector).isNull()
  }

  @Test
  fun `parent search - populates search results`() {
    coEvery { repository.search("smile") } returns listOf(grin)

    val viewModel = createViewModel()
    parentState.value = parentState.value.copy(searchActive = true, searchQuery = "smile")
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.state.value.searchResults).isEqualTo(listOf(grin))

    parentState.value = parentState.value.copy(searchActive = false, searchQuery = "")
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.state.value.searchResults).isNull()
  }
}
