/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.sample.dependencies

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.signal.devicetransfer.NewDeviceRestoreStatus
import org.signal.devicetransfer.TransferStatus

/**
 * Debug-only simulator that replays the sequence of `TransferStatus` and `NewDeviceRestoreStatus`
 * events on a timer, so the full device-transfer UX can be exercised on a single device.
 * Toggled via the "Fake device transfer" switch in the network debug overlay.
 */
object FakeDeviceTransferRunner {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var job: Job? = null

  fun start() {
    stop()
    job = scope.launch {
      val bus = EventBus.getDefault()

      delay(500)
      bus.postSticky(TransferStatus.startingUp())

      delay(1_000)
      bus.postSticky(TransferStatus.discovery())

      delay(2_000)
      bus.postSticky(TransferStatus.verificationRequired(1234567))

      // The user has up to this delay to confirm the SAS; regardless of their choice, we fake
      // the service-connected signal so the flow can progress.
      delay(6_000)
      bus.postSticky(TransferStatus.serviceConnected())

      // Progress: climb some byte counts, then transfer/restore complete.
      for (count in listOf(100L, 500L, 1_500L, 4_200L)) {
        delay(700)
        bus.post(NewDeviceRestoreStatus(count, NewDeviceRestoreStatus.State.IN_PROGRESS))
      }
      delay(500)
      bus.post(NewDeviceRestoreStatus(4_200, NewDeviceRestoreStatus.State.TRANSFER_COMPLETE))
      delay(800)
      bus.post(NewDeviceRestoreStatus(0, NewDeviceRestoreStatus.State.RESTORE_COMPLETE))
    }
  }

  fun stop() {
    job?.cancel()
    job = null
  }
}
