/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.appearance

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.signal.appsettings.appearance.AppearanceSettingsAction
import org.signal.appsettings.appearance.AppearanceSettingsEvent
import org.signal.appsettings.appearance.AppearanceSettingsState
import org.signal.appsettings.appearance.AppearanceSettingsState.Dialog
import org.signal.appsettings.appearance.AppearanceSettingsState.Theme
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class AppearanceSettingsViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private val navigationEvents = mutableListOf<AppSettingsNavigationEvent>()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    mockkObject(AppearanceSettingsRepository)

    every { AppearanceSettingsRepository.getLanguage() } returns "zz"
    every { AppearanceSettingsRepository.getTheme() } returns Theme.SYSTEM
    every { AppearanceSettingsRepository.getMessageFontSize() } returns 16
    every { AppearanceSettingsRepository.isCompactNavigationBar() } returns false
    every { AppearanceSettingsRepository.isSystemThemeAvailable() } returns true
    every { AppearanceSettingsRepository.isAppIconSupported() } returns true
    every { AppearanceSettingsRepository.applyLanguage(any()) } just Runs
    every { AppearanceSettingsRepository.setTheme(any()) } just Runs
    every { AppearanceSettingsRepository.setMessageFontSize(any()) } just Runs
    every { AppearanceSettingsRepository.setCompactNavigationBar(any()) } just Runs
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    unmockkObject(AppearanceSettingsRepository)
  }

  @Test
  fun `initial state is read out of the repository`() = runTest(testDispatcher) {
    every { AppearanceSettingsRepository.getLanguage() } returns "de"
    every { AppearanceSettingsRepository.getTheme() } returns Theme.DARK
    every { AppearanceSettingsRepository.getMessageFontSize() } returns 22
    every { AppearanceSettingsRepository.isCompactNavigationBar() } returns true
    every { AppearanceSettingsRepository.isSystemThemeAvailable() } returns false
    every { AppearanceSettingsRepository.isAppIconSupported() } returns false

    val viewModel = createViewModel()

    assertThat(viewModel.state.value).isEqualTo(
      AppearanceSettingsState(
        language = "de",
        theme = Theme.DARK,
        messageFontSize = 22,
        isCompactNavigationBar = true,
        isSystemThemeAvailable = false,
        isAppIconSupported = false
      )
    )
  }

  @Test
  fun `NavigateBackClicked navigates back`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(AppearanceSettingsEvent.NavigateBackClicked)

    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateBack)
  }

  @Test
  fun `LanguageSelected asks for confirmation before applying it`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(AppearanceSettingsEvent.LanguageSelected("de"))

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.ConfirmLanguageChange("de"))
    assertThat(viewModel.state.value.language).isEqualTo("zz")
    verify(exactly = 0) { AppearanceSettingsRepository.applyLanguage(any()) }
  }

  @Test
  fun `LanguageChangeConfirmed applies the selected language`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(AppearanceSettingsEvent.LanguageSelected("de"))
    viewModel.onEvent(AppearanceSettingsEvent.LanguageChangeConfirmed)

    verify { AppearanceSettingsRepository.applyLanguage("de") }
    assertThat(viewModel.state.value.language).isEqualTo("de")
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `LanguageChangeConfirmed does nothing without a pending language change`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(AppearanceSettingsEvent.LanguageChangeConfirmed)

    verify(exactly = 0) { AppearanceSettingsRepository.applyLanguage(any()) }
  }

  @Test
  fun `DialogDismissed cancels the language change`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(AppearanceSettingsEvent.LanguageSelected("de"))
    viewModel.onEvent(AppearanceSettingsEvent.DialogDismissed)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
    verify(exactly = 0) { AppearanceSettingsRepository.applyLanguage(any()) }
  }

  @Test
  fun `ThemeSelected stores the theme and updates the splash screen`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AppearanceSettingsEvent.ThemeSelected(Theme.DARK))

    verify { AppearanceSettingsRepository.setTheme(Theme.DARK) }
    assertThat(viewModel.state.value.theme).isEqualTo(Theme.DARK)
    assertThat(actions).isEqualTo(listOf(AppearanceSettingsAction.ApplySplashScreenTheme(Theme.DARK)))
  }

  @Test
  fun `MessageFontSizeSelected stores the font size`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AppearanceSettingsEvent.MessageFontSizeSelected(28))

    verify { AppearanceSettingsRepository.setMessageFontSize(28) }
    assertThat(viewModel.state.value.messageFontSize).isEqualTo(28)
    assertThat(actions).isEmpty()
  }

  @Test
  fun `ChatColorAndWallpaperClicked opens chat color and wallpaper`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(AppearanceSettingsEvent.ChatColorAndWallpaperClicked)

    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.AppearanceRoute.Wallpaper))
  }

  @Test
  fun `AppIconClicked opens the app icon picker`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(AppearanceSettingsEvent.AppIconClicked)

    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.AppearanceRoute.AppIconSelection))
  }

  @Test
  fun `NavigationBarSizeClicked opens the picker on the current size`() = runTest(testDispatcher) {
    every { AppearanceSettingsRepository.isCompactNavigationBar() } returns true
    val viewModel = createViewModel()

    viewModel.onEvent(AppearanceSettingsEvent.NavigationBarSizeClicked)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.NavigationBarSize(isCompact = true))
  }

  @Test
  fun `NavigationBarSizeSelected changes the choice without saving it`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(AppearanceSettingsEvent.NavigationBarSizeClicked)
    viewModel.onEvent(AppearanceSettingsEvent.NavigationBarSizeSelected(isCompact = true))

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.NavigationBarSize(isCompact = true))
    assertThat(viewModel.state.value.isCompactNavigationBar).isFalse()
    verify(exactly = 0) { AppearanceSettingsRepository.setCompactNavigationBar(any()) }
  }

  @Test
  fun `NavigationBarSizeConfirmed saves the choice and closes the picker`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(AppearanceSettingsEvent.NavigationBarSizeClicked)
    viewModel.onEvent(AppearanceSettingsEvent.NavigationBarSizeSelected(isCompact = true))
    viewModel.onEvent(AppearanceSettingsEvent.NavigationBarSizeConfirmed)

    verify { AppearanceSettingsRepository.setCompactNavigationBar(true) }
    assertThat(viewModel.state.value.isCompactNavigationBar).isTrue()
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `NavigationBarSizeConfirmed is ignored without the picker`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(AppearanceSettingsEvent.NavigationBarSizeConfirmed)

    verify(exactly = 0) { AppearanceSettingsRepository.setCompactNavigationBar(any()) }
  }

  private fun createViewModel(): AppearanceSettingsViewModel {
    return AppearanceSettingsViewModel(parentEventEmitter = { navigationEvents += it })
  }

  private fun TestScope.collectActions(actions: Flow<AppearanceSettingsAction>): List<AppearanceSettingsAction> {
    val collected = mutableListOf<AppearanceSettingsAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
