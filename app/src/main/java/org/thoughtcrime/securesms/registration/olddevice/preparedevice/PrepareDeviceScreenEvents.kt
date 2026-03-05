/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.olddevice.preparedevice

/**
 * Events emitted by the PrepareDevice screen.
 */
sealed interface PrepareDeviceScreenEvents {
  data object NavigateBack : PrepareDeviceScreenEvents
  data object BackUpNow : PrepareDeviceScreenEvents
  data object SkipAndContinue : PrepareDeviceScreenEvents
}
