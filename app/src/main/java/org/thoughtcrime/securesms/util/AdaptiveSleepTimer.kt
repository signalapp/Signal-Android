/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.util

import android.content.Context
import org.signal.core.util.SleepTimer
import org.signal.core.util.UptimeSleepTimer
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * A [SleepTimer] that decides between alarm-backed and uptime-backed sleeping on each call, since the websockets now outlive
 * the FCM/forced-websocket settings that used to be read once at construction time.
 */
class AdaptiveSleepTimer(context: Context) : SleepTimer {

  private val alarmTimer = AlarmSleepTimer(context)
  private val uptimeTimer = UptimeSleepTimer()

  override fun sleep(millis: Long) {
    val needsAlarm = !SignalStore.account.fcmEnabled || SignalStore.settings.forceWebsocketMode.isEnabled

    if (needsAlarm) {
      alarmTimer.sleep(millis)
    } else {
      uptimeTimer.sleep(millis)
    }
  }
}
