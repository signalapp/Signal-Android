/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.sticker

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.signal.mediakeyboard.MediaKeyboardAction
import org.signal.mediakeyboard.data.KeyboardSticker
import org.signal.mediakeyboard.data.KeyboardStickerPack
import org.signal.mediakeyboard.data.StickerKeyboardRepository

@OptIn(ExperimentalCoroutinesApi::class)
class StickerPageViewModelTest {

  private val testDispatcher = StandardTestDispatcher()

  private val sticker = KeyboardSticker(packId = "pack-1", packKey = "pack-1-key", stickerId = 1, emoji = "😀", image = "image-1")
  private val emptyFavorites = KeyboardStickerPack(id = StickerKeyboardRepository.FAVORITES_PACK_ID, packKey = null, title = null, cover = null, stickers = emptyList())
  private val packs = listOf(
    KeyboardStickerPack(id = "pack-1", packKey = "pack-1-key", title = "Pack One", cover = null, stickers = listOf(sticker)),
    KeyboardStickerPack(id = "pack-2", packKey = "pack-2-key", title = "Pack Two", cover = null, stickers = emptyList())
  )

  private lateinit var repository: StickerKeyboardRepository
  private lateinit var packsFlow: MutableStateFlow<List<KeyboardStickerPack>>
  private lateinit var actions: MutableList<MediaKeyboardAction>
  private lateinit var collectorScope: CoroutineScope

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    packsFlow = MutableStateFlow(packs)
    repository = mockk(relaxed = true)
    every { repository.allowStickerAnimation } returns true
    every { repository.observeStickerPacks() } returns packsFlow

    actions = mutableListOf()
    collectorScope = CoroutineScope(testDispatcher)
  }

  @After
  fun tearDown() {
    collectorScope.cancel()
    Dispatchers.resetMain()
  }

  private fun createViewModel(): StickerPageViewModel {
    val viewModel = StickerPageViewModel(repository)
    collectorScope.launch { viewModel.actions.collect { actions += it } }
    testDispatcher.scheduler.advanceUntilIdle()
    return viewModel
  }

  @Test
  fun `packs updated - selects the first pack`() {
    val viewModel = createViewModel()

    assertThat(viewModel.state.value.packs).isEqualTo(packs)
    assertThat(viewModel.state.value.selectedPackId).isEqualTo("pack-1")
  }

  @Test
  fun `packs updated - keeps the selection when it survives`() {
    val viewModel = createViewModel()
    viewModel.onEvent(StickerPageScreenEvents.PackSelected("pack-2"))
    testDispatcher.scheduler.advanceUntilIdle()

    packsFlow.value = packs.reversed()
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.state.value.selectedPackId).isEqualTo("pack-2")
  }

  @Test
  fun `pack selected - asks the grid to scroll there`() {
    val viewModel = createViewModel()
    viewModel.onEvent(StickerPageScreenEvents.PackSelected("pack-2"))
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.state.value.scrollTargetPackId).isEqualTo("pack-2")
  }

  @Test
  fun `packs updated - selects favorites once it has stickers`() {
    val favorites = emptyFavorites.copy(stickers = listOf(sticker.copy(isFavorite = true)))
    packsFlow.value = listOf(favorites) + packs
    val viewModel = createViewModel()

    assertThat(viewModel.state.value.selectedPackId).isEqualTo(StickerKeyboardRepository.FAVORITES_PACK_ID)
  }

  @Test
  fun `pack selected - an empty favorites pack asks the host for the hint instead of selecting`() {
    packsFlow.value = listOf(emptyFavorites) + packs
    val viewModel = createViewModel()
    viewModel.onEvent(StickerPageScreenEvents.PackSelected(StickerKeyboardRepository.FAVORITES_PACK_ID))
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(actions).isEqualTo(listOf(MediaKeyboardAction.EmptyFavoritesClicked))
    assertThat(viewModel.state.value.selectedPackId).isEqualTo("pack-1")
    assertThat(viewModel.state.value.scrollTargetPackId).isNull()
  }

  @Test
  fun `sticker clicked - reports the selection without recording a use`() {
    val viewModel = createViewModel()
    viewModel.onEvent(StickerPageScreenEvents.StickerClicked(sticker))
    testDispatcher.scheduler.advanceUntilIdle()

    verify(exactly = 0) { repository.onStickerUsed(any()) }
    assertThat(actions).isEqualTo(listOf(MediaKeyboardAction.StickerSelected(sticker)))
  }

  @Test
  fun `sticker send clicked - records the use and asks for a send`() {
    val viewModel = createViewModel()
    viewModel.onEvent(StickerPageScreenEvents.StickerSendClicked(sticker))
    testDispatcher.scheduler.advanceUntilIdle()

    verify { repository.onStickerUsed(sticker) }
    assertThat(actions).isEqualTo(listOf(MediaKeyboardAction.StickerSendClicked(sticker)))
  }

  @Test
  fun `view pack clicked - hands the pack's ids to the host`() {
    val viewModel = createViewModel()
    viewModel.onEvent(StickerPageScreenEvents.ViewStickerPackClicked(sticker.packId, sticker.packKey))
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(actions).isEqualTo(listOf(MediaKeyboardAction.ViewStickerPackClicked("pack-1", "pack-1-key")))
  }

  @Test
  fun `remove pack clicked - prompts instead of removing`() {
    val viewModel = createViewModel()
    viewModel.onEvent(StickerPageScreenEvents.RemoveStickerPackClicked("pack-1", "pack-1-key"))
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.state.value.confirmRemovePack).isEqualTo(ConfirmRemovePack("pack-1", "pack-1-key"))
    assertThat(actions).isEmpty()
  }

  @Test
  fun `remove pack confirmed - dismisses the prompt and hands the pack's ids to the host`() {
    val viewModel = createViewModel()
    viewModel.onEvent(StickerPageScreenEvents.RemoveStickerPackClicked("pack-1", "pack-1-key"))
    viewModel.onEvent(StickerPageScreenEvents.RemoveStickerPackConfirmed)
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.state.value.confirmRemovePack).isNull()
    assertThat(actions).isEqualTo(listOf(MediaKeyboardAction.RemoveStickerPackConfirmed("pack-1", "pack-1-key")))
  }

  @Test
  fun `remove pack canceled - dismisses the prompt without removing`() {
    val viewModel = createViewModel()
    viewModel.onEvent(StickerPageScreenEvents.RemoveStickerPackClicked("pack-1", "pack-1-key"))
    viewModel.onEvent(StickerPageScreenEvents.RemoveStickerPackCanceled)
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.state.value.confirmRemovePack).isNull()
    assertThat(actions).isEmpty()
  }

  @Test
  fun `add to favorites clicked - hands the sticker to the host`() {
    val viewModel = createViewModel()
    viewModel.onEvent(StickerPageScreenEvents.AddStickerToFavoritesClicked(sticker))
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(actions).isEqualTo(listOf(MediaKeyboardAction.AddStickerToFavoritesClicked(sticker)))
  }

  @Test
  fun `remove from favorites clicked - prompts instead of removing`() {
    val viewModel = createViewModel()
    viewModel.onEvent(StickerPageScreenEvents.RemoveStickerFromFavoritesClicked(sticker))
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.state.value.confirmRemoveFavorite).isEqualTo(sticker)
    assertThat(actions).isEmpty()
  }

  @Test
  fun `remove from favorites confirmed - dismisses the prompt and hands the sticker to the host`() {
    val viewModel = createViewModel()
    viewModel.onEvent(StickerPageScreenEvents.RemoveStickerFromFavoritesClicked(sticker))
    viewModel.onEvent(StickerPageScreenEvents.RemoveStickerFromFavoritesConfirmed)
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.state.value.confirmRemoveFavorite).isNull()
    assertThat(actions).isEqualTo(listOf(MediaKeyboardAction.RemoveStickerFromFavoritesConfirmed(sticker)))
  }

  @Test
  fun `move favorite to top clicked - hands the sticker to the host`() {
    val viewModel = createViewModel()
    viewModel.onEvent(StickerPageScreenEvents.MoveFavoriteToTopClicked(sticker))
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(actions).isEqualTo(listOf(MediaKeyboardAction.MoveFavoriteToTopClicked(sticker)))
  }

  @Test
  fun `remove from favorites canceled - dismisses the prompt without removing`() {
    val viewModel = createViewModel()
    viewModel.onEvent(StickerPageScreenEvents.RemoveStickerFromFavoritesClicked(sticker))
    viewModel.onEvent(StickerPageScreenEvents.RemoveStickerFromFavoritesCanceled)
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.state.value.confirmRemoveFavorite).isNull()
    assertThat(actions).isEmpty()
  }

  @Test
  fun `search clicked - hands off to the host`() {
    val viewModel = createViewModel()
    viewModel.onEvent(StickerPageScreenEvents.SearchClicked)
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(actions).isEqualTo(listOf(MediaKeyboardAction.StickerSearchClicked))
  }
}
