/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.devicetransfer.setup

/**
 * Mirrors `org.thoughtcrime.securesms.devicetransfer.SetupStep` for the new-device side of the
 * Wi-Fi Direct pairing state machine. `isProgress` screens show a spinner; `isError` screens
 * show the red error surface with a recovery action.
 */
enum class SetupStep(val isProgress: Boolean, val isError: Boolean) {
  INITIAL(true, false),
  PERMISSIONS_CHECK(true, false),
  PERMISSIONS_DENIED(false, true),
  LOCATION_CHECK(true, false),
  LOCATION_DISABLED(false, true),
  WIFI_CHECK(true, false),
  WIFI_DISABLED(false, true),
  WIFI_DIRECT_CHECK(true, false),
  WIFI_DIRECT_UNAVAILABLE(false, true),
  START(true, false),
  SETTING_UP(true, false),
  WAITING(true, false),
  VERIFY(false, false),
  WAITING_FOR_OTHER_TO_VERIFY(false, false),
  CONNECTED(true, false),
  TROUBLESHOOTING(false, false),
  ERROR(false, true)
}
