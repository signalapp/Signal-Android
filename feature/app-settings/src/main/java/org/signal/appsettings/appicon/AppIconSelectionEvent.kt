/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.appicon

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface AppIconSelectionEvent {

  /** The user tapped the navigation (back) icon. */
  data object NavigateBackClicked : AppIconSelectionEvent

  /** The user tapped the caveat text explaining where the icon shows up. */
  data object LearnMoreClicked : AppIconSelectionEvent

  /** The user tapped an icon in the grid. */
  data class IconClicked(val preset: AppIconPreset) : AppIconSelectionEvent

  /** The user confirmed the icon change in the dialog. */
  data object ChangeConfirmed : AppIconSelectionEvent

  /** The user cancelled or dismissed the confirmation dialog. */
  data object DialogDismissed : AppIconSelectionEvent
}
