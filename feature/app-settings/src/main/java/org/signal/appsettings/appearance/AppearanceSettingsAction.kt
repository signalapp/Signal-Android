/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.appearance

/**
 * One-shot side effects that need an Activity, and therefore have to be carried out by the host rather than the screen
 * itself. Navigation doesn't come through here: the view model sends it straight to its parent.
 *
 * Actions are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface AppearanceSettingsAction {

  /** The theme changed, so the activity's splash screen theme has to follow it. */
  data class ApplySplashScreenTheme(val theme: AppearanceSettingsState.Theme) : AppearanceSettingsAction

  /** Opens [url] in the browser. */
  data class OpenBrowserLink(val url: String) : AppearanceSettingsAction

  /** Opens the system settings page of the Play Store app. */
  data object OpenPlayStoreSettings : AppearanceSettingsAction

  /** Opens the system settings page of the Play Services app. */
  data object OpenPlayServicesSettings : AppearanceSettingsAction

  /** Play wants the user to approve the language pack download; show its dialog for [sessionId]. */
  data class ConfirmLanguageDownload(val sessionId: Int) : AppearanceSettingsAction
}
