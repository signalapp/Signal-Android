/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.push

import org.signal.libsignal.net.MismatchedDeviceException

data class MismatchedDevices(
  val missingDevices: List<Int> = emptyList(),
  val extraDevices: List<Int> = emptyList()
) {
  companion object {
    @JvmStatic
    fun fromLibSignal(entry: MismatchedDeviceException.Entry): MismatchedDevices {
      return MismatchedDevices(entry.missingDevices.toList(), entry.extraDevices.toList())
    }
  }
}
