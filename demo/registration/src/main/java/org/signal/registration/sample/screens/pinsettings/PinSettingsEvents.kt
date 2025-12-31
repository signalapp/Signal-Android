/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.sample.screens.pinsettings

sealed interface PinSettingsEvents {
  data class SetPin(val pin: String) : PinSettingsEvents
  data object ToggleRegistrationLock : PinSettingsEvents
  data object TogglePinsOptOut : PinSettingsEvents
  data object Back : PinSettingsEvents
  data object DismissMessage : PinSettingsEvents
}
