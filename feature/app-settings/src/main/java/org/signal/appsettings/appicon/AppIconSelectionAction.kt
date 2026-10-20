/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.appicon

/**
 * One-shot side effects that have to be carried out by the host rather than the screen itself.
 *
 * Actions are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface AppIconSelectionAction {

  /** Leave the screen. */
  data object NavigateBack : AppIconSelectionAction

  /** Open the tutorial explaining where a replacement icon shows up. */
  data object NavigateToTutorial : AppIconSelectionAction
}
