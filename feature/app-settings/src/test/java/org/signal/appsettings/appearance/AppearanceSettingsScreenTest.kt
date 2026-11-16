/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.appearance

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.ui.compose.Dialogs

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AppearanceSettingsScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val events = mutableListOf<AppearanceSettingsEvent>()

  @Test
  fun `clicking the navigation icon emits NavigateBackClicked`() {
    setContent(AppearanceSettingsState())

    composeTestRule.onNodeWithContentDescription("Navigate up").performClick()

    assertThat(events).containsExactly(AppearanceSettingsEvent.NavigateBackClicked)
  }

  @Test
  fun `picking a language emits LanguageSelected`() {
    setContent(AppearanceSettingsState(language = "zz"))

    composeTestRule.onNodeWithTag(AppearanceSettingsTestTags.ROW_LANGUAGE).performClick()
    composeTestRule.onNodeWithText("English").performClick()

    assertThat(events).contains(AppearanceSettingsEvent.LanguageSelected("en"))
  }

  @Test
  fun `picking a theme emits ThemeSelected`() {
    setContent(AppearanceSettingsState(theme = AppearanceSettingsState.Theme.SYSTEM))

    composeTestRule.onNodeWithTag(AppearanceSettingsTestTags.ROW_THEME).performClick()
    composeTestRule.onNodeWithText("Dark").performClick()

    assertThat(events).contains(AppearanceSettingsEvent.ThemeSelected(AppearanceSettingsState.Theme.DARK))
  }

  @Test
  fun `the system theme is not offered when the device does not support it`() {
    setContent(AppearanceSettingsState(language = "en", theme = AppearanceSettingsState.Theme.LIGHT, isSystemThemeAvailable = false))

    composeTestRule.onNodeWithTag(AppearanceSettingsTestTags.ROW_THEME).performClick()

    composeTestRule.onNodeWithText("Dark").assertIsDisplayed()
    composeTestRule.onNodeWithText("System default").assertDoesNotExist()
  }

  @Test
  fun `clicking chat color and wallpaper emits ChatColorAndWallpaperClicked`() {
    setContent(AppearanceSettingsState())

    composeTestRule.onNodeWithTag(AppearanceSettingsTestTags.ROW_CHAT_COLOR_AND_WALLPAPER).performClick()

    assertThat(events).containsExactly(AppearanceSettingsEvent.ChatColorAndWallpaperClicked)
  }

  @Test
  fun `clicking app icon emits AppIconClicked`() {
    setContent(AppearanceSettingsState(isAppIconSupported = true))

    composeTestRule.onNodeWithTag(AppearanceSettingsTestTags.ROW_APP_ICON).performClick()

    assertThat(events).containsExactly(AppearanceSettingsEvent.AppIconClicked)
  }

  @Test
  fun `app icon row is hidden when the device does not support it`() {
    setContent(AppearanceSettingsState(isAppIconSupported = false))

    composeTestRule.onNodeWithTag(AppearanceSettingsTestTags.ROW_APP_ICON).assertDoesNotExist()
  }

  @Test
  fun `picking a message font size emits MessageFontSizeSelected`() {
    setContent(AppearanceSettingsState(messageFontSize = 16))

    scrollTo(AppearanceSettingsTestTags.ROW_MESSAGE_FONT_SIZE)
    composeTestRule.onNodeWithTag(AppearanceSettingsTestTags.ROW_MESSAGE_FONT_SIZE).performClick()
    composeTestRule.onNodeWithText("Extra large").performClick()

    assertThat(events).contains(AppearanceSettingsEvent.MessageFontSizeSelected(28))
  }

  @Test
  fun `navigation bar size row shows the current size`() {
    setContent(AppearanceSettingsState(isCompactNavigationBar = true))

    scrollTo(AppearanceSettingsTestTags.ROW_NAVIGATION_BAR_SIZE)
    composeTestRule.onNodeWithText("Compact").assertIsDisplayed()
  }

  @Test
  fun `clicking navigation bar size emits NavigationBarSizeClicked`() {
    setContent(AppearanceSettingsState())

    scrollTo(AppearanceSettingsTestTags.ROW_NAVIGATION_BAR_SIZE)
    composeTestRule.onNodeWithTag(AppearanceSettingsTestTags.ROW_NAVIGATION_BAR_SIZE).performClick()

    assertThat(events).containsExactly(AppearanceSettingsEvent.NavigationBarSizeClicked)
  }

  @Test
  fun `confirming the language change dialog emits LanguageChangeConfirmed`() {
    setContent(AppearanceSettingsState(dialog = AppearanceSettingsState.Dialog.ConfirmLanguageChange("de")))

    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON).performClick()

    assertThat(events).containsExactly(AppearanceSettingsEvent.LanguageChangeConfirmed)
  }

  @Test
  fun `cancelling the language change dialog emits DialogDismissed`() {
    setContent(AppearanceSettingsState(dialog = AppearanceSettingsState.Dialog.ConfirmLanguageChange("de")))

    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_DISMISS_BUTTON).performClick()

    assertThat(events).containsExactly(AppearanceSettingsEvent.DialogDismissed)
  }

  @Test
  fun `picking a size in the navigation bar size dialog emits NavigationBarSizeSelected`() {
    setContent(AppearanceSettingsState(dialog = AppearanceSettingsState.Dialog.NavigationBarSize(isCompact = false)))

    composeTestRule.onNodeWithTag(AppearanceSettingsTestTags.BUTTON_NAVIGATION_BAR_COMPACT).performClick()
    composeTestRule.onNodeWithTag(AppearanceSettingsTestTags.BUTTON_NAVIGATION_BAR_NORMAL).performClick()

    assertThat(events).containsExactly(
      AppearanceSettingsEvent.NavigationBarSizeSelected(isCompact = true),
      AppearanceSettingsEvent.NavigationBarSizeSelected(isCompact = false)
    )
  }

  @Test
  fun `the navigation bar size dialog marks the current choice`() {
    setContent(AppearanceSettingsState(dialog = AppearanceSettingsState.Dialog.NavigationBarSize(isCompact = true)))

    composeTestRule.onNodeWithTag(AppearanceSettingsTestTags.BUTTON_NAVIGATION_BAR_COMPACT).assertIsSelected()
    composeTestRule.onNodeWithTag(AppearanceSettingsTestTags.BUTTON_NAVIGATION_BAR_NORMAL).assertIsNotSelected()
  }

  @Test
  fun `confirming the navigation bar size dialog emits NavigationBarSizeConfirmed`() {
    setContent(AppearanceSettingsState(dialog = AppearanceSettingsState.Dialog.NavigationBarSize(isCompact = true)))

    composeTestRule.onNodeWithTag(AppearanceSettingsTestTags.BUTTON_NAVIGATION_BAR_CONFIRM).performClick()

    assertThat(events).containsExactly(AppearanceSettingsEvent.NavigationBarSizeConfirmed)
  }

  private fun setContent(state: AppearanceSettingsState) {
    composeTestRule.setContent {
      AppearanceSettingsScreen(
        state = state,
        onEvent = { events += it }
      )
    }
  }

  private fun scrollTo(testTag: String) {
    composeTestRule.onNodeWithTag(AppearanceSettingsTestTags.SCROLLER)
      .performScrollToNode(hasTestTag(testTag))
  }
}
