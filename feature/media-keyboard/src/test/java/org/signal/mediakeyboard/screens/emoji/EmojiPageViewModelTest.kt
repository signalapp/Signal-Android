/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.emoji

import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.signal.mediakeyboard.MediaKeyboardAction
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
  private lateinit var hostSearchQuery: MutableStateFlow<String?>
  private lateinit var actions: MutableList<MediaKeyboardAction>
  private lateinit var collectorScope: CoroutineScope

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    repository = mockk(relaxed = true)
    coEvery { repository.getEmojiPages() } returns listOf(EmojiCategoryPage(EmojiKeyboardCategory.PEOPLE, listOf(grin, thumbsUp)))
    coEvery { repository.getRecentEmoji() } returns listOf(KeyboardEmoji("😂"))
    coEvery { repository.getPreferredVariations() } returns emptyMap()

    hostSearchQuery = MutableStateFlow(null)
    actions = mutableListOf()
    collectorScope = CoroutineScope(testDispatcher)
  }

  @After
  fun tearDown() {
    collectorScope.cancel()
    Dispatchers.resetMain()
  }

  private fun createViewModel(searchQuery: Flow<String?>? = hostSearchQuery): EmojiPageViewModel {
    val viewModel = EmojiPageViewModel(repository, searchQuery)
    collectorScope.launch { viewModel.actions.collect { actions += it } }
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
  fun `refresh recents - picks up newly used emoji`() {
    val viewModel = createViewModel()
    coEvery { repository.getRecentEmoji() } returns listOf(grin, KeyboardEmoji("😂"))

    viewModel.onEvent(EmojiPageScreenEvents.RefreshRecents)
    testDispatcher.scheduler.advanceUntilIdle()

    val pages = viewModel.state.value.pages
    assertThat(pages.map { it.category }).isEqualTo(listOf(EmojiKeyboardCategory.RECENTS, EmojiKeyboardCategory.PEOPLE))
    assertThat(pages.first().emoji).isEqualTo(listOf(grin, KeyboardEmoji("😂")))
  }

  @Test
  fun `refresh recents - adds recents page when there were none`() {
    coEvery { repository.getRecentEmoji() } returns emptyList()
    val viewModel = createViewModel()
    coEvery { repository.getRecentEmoji() } returns listOf(grin)

    viewModel.onEvent(EmojiPageScreenEvents.RefreshRecents)
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.state.value.pages.map { it.category }).isEqualTo(listOf(EmojiKeyboardCategory.RECENTS, EmojiKeyboardCategory.PEOPLE))
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
  fun `host search - populates search results`() {
    coEvery { repository.search("smile") } returns listOf(grin)

    val viewModel = createViewModel()
    hostSearchQuery.value = "smile"
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.state.value.searchResults).isEqualTo(listOf(grin))

    hostSearchQuery.value = null
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.state.value.searchResults).isNull()
  }

  @Test
  fun `host search - blank query shows recents in place of results`() {
    val viewModel = createViewModel()
    hostSearchQuery.value = ""
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.state.value.searchResults).isEqualTo(listOf(KeyboardEmoji("😂")))
  }

  @Test
  fun `field search - typing searches and clearing goes back to the grid`() {
    coEvery { repository.search("smile") } returns listOf(grin)
    val viewModel = createViewModel(searchQuery = null)

    viewModel.searchFieldState.setTextAndPlaceCursorAtEnd(" smile ")
    Snapshot.sendApplyNotifications()
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.state.value.searchResults).isEqualTo(listOf(grin))
    assertThat(viewModel.state.value.searchQuery).isEqualTo("smile")

    viewModel.searchFieldState.clearText()
    Snapshot.sendApplyNotifications()
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.state.value.searchResults).isNull()
  }

  @Test
  fun `field search - blank field is not searching`() {
    val viewModel = createViewModel(searchQuery = null)

    viewModel.searchFieldState.setTextAndPlaceCursorAtEnd("   ")
    Snapshot.sendApplyNotifications()
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.state.value.searchResults).isNull()
  }

  @Test
  fun `initialize - this message leads, ahead of recents`() {
    val thisMessage = EmojiCategoryPage(EmojiKeyboardCategory.THIS_MESSAGE, listOf(thumbsUp))
    coEvery { repository.getEmojiPages() } returns listOf(thisMessage, EmojiCategoryPage(EmojiKeyboardCategory.PEOPLE, listOf(grin)))

    val viewModel = createViewModel()

    val categories = viewModel.state.value.pages.map { it.category }
    assertThat(categories).isEqualTo(listOf(EmojiKeyboardCategory.THIS_MESSAGE, EmojiKeyboardCategory.RECENTS, EmojiKeyboardCategory.PEOPLE))
  }
}
