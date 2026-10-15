/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.appearance

/**
 * Everything [AppearanceSettingsScreen] needs to render.
 */
data class AppearanceSettingsState(
  val language: String = "zz",
  val theme: Theme = Theme.SYSTEM,
  val messageFontSize: Int = 16,
  val isCompactNavigationBar: Boolean = false,
  val isSystemThemeAvailable: Boolean = true,
  val isAppIconSupported: Boolean = true,
  val dialog: Dialog = Dialog.None
) {

  /** The themes the user can choose from. Following the system theme needs OS support. */
  val availableThemes: List<Theme>
    get() = if (isSystemThemeAvailable) Theme.entries else Theme.entries - Theme.SYSTEM

  enum class Theme {
    SYSTEM,
    LIGHT,
    DARK
  }

  /** Whichever dialog the screen is showing, if any. */
  sealed interface Dialog {
    data object None : Dialog

    /** Warns that switching to [language] restarts the app. */
    data class ConfirmLanguageChange(val language: String) : Dialog
  }
}
