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

  sealed interface LanguageDownloadFailure {
    /** A network or Play error that a retry may fix. [errorCode] is Play's, shown so it can be reported. */
    data class RetryableError(val errorCode: Int) : LanguageDownloadFailure

    /** Not enough space on the device. */
    data object Storage : LanguageDownloadFailure

    /** The Play Store or Play Services is missing. */
    data object PlayUnavailable : LanguageDownloadFailure

    /** The Play Store is installed but disabled. */
    data object PlayStoreDisabled : LanguageDownloadFailure

    /** Play Services is installed but disabled. */
    data object PlayServicesDisabled : LanguageDownloadFailure
  }

  /** Whichever dialog the screen is showing, if any. */
  sealed interface Dialog {
    data object None : Dialog

    /** Warns that switching to [language] restarts the app. When [needsDownload] is true, the language pack is downloaded first. */
    data class ConfirmLanguageChange(val language: String, val needsDownload: Boolean = false) : Dialog

    data object DownloadingLanguage : Dialog

    /** Downloading the language pack for [language] failed; [reason] decides the message and whether a retry is offered. */
    data class LanguageDownloadFailed(val language: String, val reason: LanguageDownloadFailure) : Dialog

    /** Lets the user choose between a full-sized and a compact navigation bar. [isCompact] is the choice so far. */
    data class NavigationBarSize(val isCompact: Boolean) : Dialog
  }
}
