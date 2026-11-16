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
}
