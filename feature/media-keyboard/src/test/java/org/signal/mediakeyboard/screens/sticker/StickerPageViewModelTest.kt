/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.sticker

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.every
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
import org.signal.mediakeyboard.data.KeyboardSticker
import org.signal.mediakeyboard.data.KeyboardStickerPack
import org.signal.mediakeyboard.data.StickerKeyboardRepository

@OptIn(ExperimentalCoroutinesApi::class)
class StickerPageViewModelTest {

  private val testDispatcher = StandardTestDispatcher()

  private val sticker = KeyboardSticker(packId = "pack-1", packKey = "pack-1-key", stickerId = 1, emoji = "😀", image = "image-1")
  private val packs = listOf(
    KeyboardStickerPack(id = "pack-1", title = "Pack One", cover = null, stickers = listOf(sticker)),
    KeyboardStickerPack(id = "pack-2", title = "Pack Two", cover = null, stickers = emptyList())
  )

  private lateinit var repository: StickerKeyboardRepository
  private lateinit var packsFlow: MutableStateFlow<List<KeyboardStickerPack>>
  private lateinit var actions: MutableList<MediaKeyboardAction>

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    packsFlow = MutableStateFlow(packs)
    repository = mockk(relaxed = true)
    every { repository.allowStickerAnimation } returns true
    every { repository.observeStickerPacks() } returns packsFlow

    actions = mutableListOf()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun createViewModel(): StickerPageViewModel {
    val viewModel = StickerPageViewModel(repository, actions::add)
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
  fun `sticker clicked - records the use and reports the selection`() {
    val viewModel = createViewModel()
    viewModel.onEvent(StickerPageScreenEvents.StickerClicked(sticker))
    testDispatcher.scheduler.advanceUntilIdle()

    verify { repository.onStickerUsed(sticker) }
    assertThat(actions).isEqualTo(listOf(MediaKeyboardAction.StickerSelected(sticker)))
  }

  @Test
  fun `view pack clicked - hands the pack's ids to the host`() {
    val viewModel = createViewModel()
    viewModel.onEvent(StickerPageScreenEvents.ViewStickerPackClicked(sticker.packId, sticker.packKey))
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(actions).isEqualTo(listOf(MediaKeyboardAction.ViewStickerPackClicked("pack-1", "pack-1-key")))
  }

  @Test
  fun `search clicked - hands off to the host`() {
    val viewModel = createViewModel()
    viewModel.onEvent(StickerPageScreenEvents.SearchClicked)
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(actions).isEqualTo(listOf(MediaKeyboardAction.StickerSearchClicked))
  }
}
