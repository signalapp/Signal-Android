/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.usernamecolorpicker

import org.signal.appsettings.usernamelinks.UsernameQrCodeColorScheme

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface UsernameLinkQrColorPickerEvent {

  /** The user tapped the navigation (back) icon. */
  data object NavigateBackClicked : UsernameLinkQrColorPickerEvent

  /** The user tapped the done button. */
  data object DoneClicked : UsernameLinkQrColorPickerEvent

  /** The user tapped a color in the picker. */
  data class ColorSelected(val colorScheme: UsernameQrCodeColorScheme) : UsernameLinkQrColorPickerEvent
}
