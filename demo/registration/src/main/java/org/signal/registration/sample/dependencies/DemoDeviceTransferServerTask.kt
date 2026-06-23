/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.sample.dependencies

import android.content.Context
import org.greenrobot.eventbus.EventBus
import org.signal.core.util.logging.Log
import org.signal.devicetransfer.NewDeviceRestoreStatus
import org.signal.devicetransfer.ServerTask
import java.io.InputStream

/**
 * Real [ServerTask] for the demo: parses incoming Signal backup frames using the user's AEP as
 * the passphrase, counts frames, and exits cleanly when the `end = true` frame arrives (matching
 * production `FullBackupImporter` behavior). Frame contents are discarded — we only care about
 * driving the transfer protocol and reporting progress.
 */
class DemoDeviceTransferServerTask(private val passphrase: String) : ServerTask {

  companion object {
    private val TAG = Log.tag(DemoDeviceTransferServerTask::class)
    private const val PROGRESS_POST_INTERVAL_MS = 250L
  }

  override fun run(context: Context, inputStream: InputStream) {
    val bus = EventBus.getDefault()
    Log.i(TAG, "Reading incoming backup stream with ${passphrase.length}-char passphrase")

    val reader: DemoBackupStreamReader = try {
      DemoBackupStreamReader(inputStream, passphrase)
    } catch (t: Throwable) {
      Log.w(TAG, "Failed to initialize backup reader", t)
      bus.post(NewDeviceRestoreStatus(0, NewDeviceRestoreStatus.State.FAILURE_UNKNOWN))
      return
    }

    var frames = 0L
    var lastPostMs = System.currentTimeMillis()
    try {
      while (true) {
        val info = reader.readFrame()
        frames++

        val bodyLength = info.attachmentBodyLength
        if (bodyLength != null && bodyLength > 0) {
          reader.drainAttachmentBody(bodyLength)
        }

        val now = System.currentTimeMillis()
        if (now - lastPostMs >= PROGRESS_POST_INTERVAL_MS) {
          lastPostMs = now
          Log.d(TAG, "Received $frames frames")
          bus.post(NewDeviceRestoreStatus(frames, NewDeviceRestoreStatus.State.IN_PROGRESS))
        }

        if (info.end) {
          Log.i(TAG, "Received end-of-backup frame at $frames — transfer complete")
          break
        }
      }
    } catch (t: Throwable) {
      Log.w(TAG, "Backup stream read failed after $frames frames", t)
      bus.post(NewDeviceRestoreStatus(frames, NewDeviceRestoreStatus.State.FAILURE_UNKNOWN))
      return
    }

    Log.i(TAG, "Transfer stream drained cleanly, frames=$frames")
    bus.post(NewDeviceRestoreStatus(frames, NewDeviceRestoreStatus.State.TRANSFER_COMPLETE))
    bus.post(NewDeviceRestoreStatus(frames, NewDeviceRestoreStatus.State.RESTORE_COMPLETE))
  }
}
