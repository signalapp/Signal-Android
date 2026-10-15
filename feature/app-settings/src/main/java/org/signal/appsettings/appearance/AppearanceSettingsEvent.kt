/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.appearance

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface AppearanceSettingsEvent {

  /** The user tapped the navigation (back) icon. */
  data object NavigateBackClicked : AppearanceSettingsEvent

  /** The user picked [language] from the language list, which asks them to confirm the restart first. */
  data class LanguageSelected(val language: String) : AppearanceSettingsEvent

  /** The user accepted restarting the app to apply the new language. */
  data object LanguageChangeConfirmed : AppearanceSettingsEvent

  /** The user picked a theme from the theme list. */
  data class ThemeSelected(val theme: AppearanceSettingsState.Theme) : AppearanceSettingsEvent

  /** The user tapped the chat color and wallpaper row. */
  data object ChatColorAndWallpaperClicked : AppearanceSettingsEvent

  /** The user tapped the app icon row. */
  data object AppIconClicked : AppearanceSettingsEvent

  /** The user picked a message font size, in sp. */
  data class MessageFontSizeSelected(val size: Int) : AppearanceSettingsEvent

  /** The user tapped the navigation bar size row. */
  data object NavigationBarSizeClicked : AppearanceSettingsEvent

  /** The navigation bar size picker saved a new size. */
  data object NavigationBarSizeChanged : AppearanceSettingsEvent

  /** Dismisses whatever is in [AppearanceSettingsState.dialog]. */
  data object DialogDismissed : AppearanceSettingsEvent
}
