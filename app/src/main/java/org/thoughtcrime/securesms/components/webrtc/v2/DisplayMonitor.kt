/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import android.hardware.display.DisplayManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

object DisplayMonitor {

  /**
   * Emits a flow of events from a [DisplayManager.DisplayListener]
   * callback.
   */
  fun monitor(displayManager: DisplayManager): Flow<MonitorEvent> {
    return callbackFlow {
      val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
          trySendBlocking(MonitorEvent.Added(displayId))
        }

        override fun onDisplayRemoved(displayId: Int) {
          trySendBlocking(MonitorEvent.Removed(displayId))
        }

        override fun onDisplayChanged(displayId: Int) {
          trySendBlocking(MonitorEvent.Changed(displayId))
        }
      }

      displayManager.registerDisplayListener(displayListener, null)
      awaitClose {
        displayManager.unregisterDisplayListener(displayListener)
      }
    }
  }

  sealed interface MonitorEvent {
    val displayId: Int

    data class Added(override val displayId: Int) : MonitorEvent
    data class Removed(override val displayId: Int) : MonitorEvent
    data class Changed(override val displayId: Int) : MonitorEvent
  }
}
