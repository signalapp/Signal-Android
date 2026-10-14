/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.devicetransfer.app

import org.signal.devicetransfer.TransferStatus.TransferMode
import org.signal.devicetransfer.WifiDirect

data class DeviceTransferTestState(
  val deviceDescription: String = "",
  val libsignalVersion: String = "",
  val readiness: Readiness = Readiness(),
  val payloadSize: PayloadSize = PayloadSize.MB_256,
  val role: Role = Role.NONE,
  val transferMode: TransferMode? = null,
  val progress: Progress? = null,
  val result: TransferResult? = null,
  val log: List<LogEntry> = emptyList(),
  val dialog: Dialog = Dialog.None
) {

  val isRunning: Boolean
    get() = role != Role.NONE

  val canStart: Boolean
    get() = !isRunning && readiness.availability == WifiDirect.AvailableStatus.AVAILABLE

  /**
   * What the device needs for Wi-Fi Direct. [usesNearbyPermission] is true on API 33+, where the
   * nearby Wi-Fi devices permission replaces fine location.
   */
  data class Readiness(
    val availability: WifiDirect.AvailableStatus? = null,
    val usesNearbyPermission: Boolean = true,
    val wifiDirectPermissionGranted: Boolean = false,
    val notificationPermissionGranted: Boolean = false,
    val wifiEnabled: Boolean = false,
    val locationEnabled: Boolean = false
  ) {
    val hasMissingPermissions: Boolean
      get() = !wifiDirectPermissionGranted || !notificationPermissionGranted
  }

  enum class PayloadSize(val bytes: Long) {
    MB_10(10L * 1024 * 1024),
    MB_256(256L * 1024 * 1024),
    GB_1(1024L * 1024 * 1024)
  }

  enum class Role {
    NONE,
    SERVER,
    CLIENT
  }

  data class Progress(val bytes: Long, val totalBytes: Long, val elapsedMillis: Long) {
    val fraction: Float
      get() = if (totalBytes > 0) bytes.toFloat() / totalBytes else 0f

    val bytesPerSecond: Long
      get() = if (elapsedMillis > 0) bytes * 1000 / elapsedMillis else 0
  }

  sealed interface TransferResult {
    data class Sent(val bytes: Long, val durationMillis: Long) : TransferResult
    data class Received(val bytes: Long, val durationMillis: Long, val checksumMatches: Boolean) : TransferResult
    data class Failed(val message: String) : TransferResult
  }

  data class LogEntry(val timestampMillis: Long, val message: String)

  sealed interface Dialog {
    data object None : Dialog

    data class Verification(val code: Int) : Dialog
  }
}
