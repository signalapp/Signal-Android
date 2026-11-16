/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.push

import org.signal.libsignal.net.MismatchedDeviceException

data class StaleDevices(
  val staleDevices: List<Int> = emptyList()
) {
  companion object {
    @JvmStatic
    fun fromLibSignal(entry: MismatchedDeviceException.Entry): StaleDevices {
      return StaleDevices(entry.staleDevices.toList())
    }
  }
}
