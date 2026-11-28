/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.devicetransfer.app

import android.app.Application
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import org.signal.core.util.logging.AndroidLogger
import org.signal.core.util.logging.Log

class DeviceTransferApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    Log.initialize(AndroidLogger)

    val channel = NotificationChannelCompat.Builder(DeviceTransferTestRepository.NOTIFICATION_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
      .setName("Device transfer")
      .build()
    NotificationManagerCompat.from(this).createNotificationChannel(channel)
  }
}
