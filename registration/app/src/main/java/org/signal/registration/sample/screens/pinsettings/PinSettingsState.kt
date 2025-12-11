/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.sample.screens.pinsettings

data class PinSettingsState(
  val hasPinSet: Boolean = false,
  val registrationLockEnabled: Boolean = false,
  val loading: Boolean = false,
  val toastMessage: String? = null
)
