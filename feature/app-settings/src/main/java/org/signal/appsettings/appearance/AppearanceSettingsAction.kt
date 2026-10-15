/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.appearance

/**
 * One-shot side effects that have to be carried out by the host rather than the screen itself.
 *
 * Actions are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface AppearanceSettingsAction {

  /** Leave the screen. */
  data object NavigateBack : AppearanceSettingsAction

  /** Open the chat color and wallpaper settings. */
  data object NavigateToChatColorAndWallpaper : AppearanceSettingsAction

  /** Open the app icon picker. */
  data object NavigateToAppIcon : AppearanceSettingsAction

  /** Show the navigation bar size picker. */
  data object ShowNavigationBarSizePicker : AppearanceSettingsAction

  /** The theme changed, so the activity's splash screen theme has to follow it. */
  data class ApplySplashScreenTheme(val theme: AppearanceSettingsState.Theme) : AppearanceSettingsAction
}
