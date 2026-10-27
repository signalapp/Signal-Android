/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.profiles.manage.about

import androidx.lifecycle.SavedStateHandle
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.signal.appsettings.about.EditAboutEvent
import org.signal.appsettings.about.EditAboutState
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class EditAboutViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private val navigationEvents = mutableListOf<AppSettingsNavigationEvent>()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    mockkObject(EditAboutRepository)

    every { EditAboutRepository.getAbout() } returns "Speak freely"
    every { EditAboutRepository.getAboutEmoji() } returns "👋"
    coEvery { EditAboutRepository.setAbout(any(), any()) } returns EditAboutRepository.SaveResult.SUCCESS
  }

  @After
  fun tearDown() {
    unmockkObject(EditAboutRepository)
    Dispatchers.resetMain()
  }

  @Test
  fun `initial state is the current about`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    assertThat(viewModel.state.value).isEqualTo(EditAboutState(about = "Speak freely", emoji = "👋"))
  }

  @Test
  fun `unsaved edits are restored from the saved state`() = runTest(testDispatcher) {
    val savedStateHandle = SavedStateHandle()
    createViewModel(savedStateHandle).apply {
      onEvent(EditAboutEvent.AboutChanged("Out to lunch"))
      onEvent(EditAboutEvent.EmojiSelected("☕"))
    }

    val restored = createViewModel(savedStateHandle)

    assertThat(restored.state.value).isEqualTo(EditAboutState(about = "Out to lunch", emoji = "☕"))
  }

  @Test
  fun `AboutChanged caps the about in grapheme clusters`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(EditAboutEvent.AboutChanged("a".repeat(EditAboutState.ABOUT_MAX_GRAPHEMES + 10)))

    assertThat(viewModel.state.value.about).isEqualTo("a".repeat(EditAboutState.ABOUT_MAX_GRAPHEMES))
  }

  @Test
  fun `EmojiButtonClicked opens the emoji picker`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(EditAboutEvent.EmojiButtonClicked)

    assertThat(viewModel.state.value.showEmojiPicker).isTrue()
  }

  @Test
  fun `EmojiSelected replaces only the emoji and closes the picker`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(EditAboutEvent.EmojiButtonClicked)
    viewModel.onEvent(EditAboutEvent.EmojiSelected("☕"))

    assertThat(viewModel.state.value).isEqualTo(EditAboutState(about = "Speak freely", emoji = "☕"))
  }

  @Test
  fun `EmojiPickerDismissed closes the picker and keeps the emoji`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(EditAboutEvent.EmojiButtonClicked)
    viewModel.onEvent(EditAboutEvent.EmojiPickerDismissed)

    assertThat(viewModel.state.value).isEqualTo(EditAboutState(about = "Speak freely", emoji = "\uD83D\uDC4B"))
  }

  @Test
  fun `PresetClicked replaces the emoji and the about`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(EditAboutEvent.PresetClicked(emoji = "🚀", about = "Working on something new"))

    assertThat(viewModel.state.value).isEqualTo(EditAboutState(about = "Working on something new", emoji = "🚀"))
  }

  @Test
  fun `ClearClicked empties the emoji and the about`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(EditAboutEvent.ClearClicked)

    assertThat(viewModel.state.value).isEqualTo(EditAboutState())
  }

  @Test
  fun `SaveClicked saves the about and navigates back`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(EditAboutEvent.AboutChanged("Out to lunch"))
    viewModel.onEvent(EditAboutEvent.SaveClicked)

    coVerify { EditAboutRepository.setAbout(about = "Out to lunch", emoji = "👋") }
    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateBack)
  }

  @Test
  fun `an empty about can be saved, clearing it`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(EditAboutEvent.ClearClicked)
    viewModel.onEvent(EditAboutEvent.SaveClicked)

    coVerify { EditAboutRepository.setAbout(about = "", emoji = "") }
    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateBack)
  }

  @Test
  fun `a network failure stays on the screen and shows a snackbar`() = runTest(testDispatcher) {
    coEvery { EditAboutRepository.setAbout(any(), any()) } returns EditAboutRepository.SaveResult.NETWORK_FAILURE
    val viewModel = createViewModel()

    viewModel.onEvent(EditAboutEvent.SaveClicked)

    assertThat(viewModel.state.value.saving).isFalse()
    assertThat(viewModel.state.value.snackbar).isEqualTo(EditAboutState.Snackbar.NetworkFailure)
    assertThat(navigationEvents).isEmpty()
  }

  @Test
  fun `SnackbarDismissed clears the snackbar`() = runTest(testDispatcher) {
    coEvery { EditAboutRepository.setAbout(any(), any()) } returns EditAboutRepository.SaveResult.NETWORK_FAILURE
    val viewModel = createViewModel()

    viewModel.onEvent(EditAboutEvent.SaveClicked)
    viewModel.onEvent(EditAboutEvent.SnackbarDismissed)

    assertThat(viewModel.state.value.snackbar).isEqualTo(EditAboutState.Snackbar.None)
  }

  @Test
  fun `NavigateBackClicked navigates back without saving`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(EditAboutEvent.NavigateBackClicked)

    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateBack)
    coVerify(exactly = 0) { EditAboutRepository.setAbout(any(), any()) }
  }

  private fun createViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()): EditAboutViewModel {
    return EditAboutViewModel(
      parentEventEmitter = { navigationEvents += it },
      savedStateHandle = savedStateHandle
    )
  }
}
