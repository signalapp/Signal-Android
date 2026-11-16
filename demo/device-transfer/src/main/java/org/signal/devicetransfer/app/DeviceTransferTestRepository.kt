/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.devicetransfer.app

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.signal.core.util.PendingIntentFlags
import org.signal.core.util.eventbus.EventBus
import org.signal.devicetransfer.DeviceToDeviceTransferService
import org.signal.devicetransfer.DeviceToDeviceTransferService.TransferNotificationData
import org.signal.devicetransfer.TransferStatus
import org.signal.devicetransfer.WifiDirect

class DeviceTransferTestRepository(context: Context) {

  companion object {
    const val NOTIFICATION_CHANNEL_ID = "DEVICE_TO_DEVICE_TRANSFER"
    private const val NOTIFICATION_ID = 1337
  }

  private val context = context.applicationContext

  val deviceDescription: String = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

  val libsignalVersion: String = BuildConfig.LIBSIGNAL_VERSION

  fun getReadiness(): DeviceTransferTestState.Readiness {
    return DeviceTransferTestState.Readiness(
      availability = WifiDirect.getAvailability(context),
      usesNearbyPermission = Build.VERSION.SDK_INT >= 33,
      wifiDirectPermissionGranted = isGranted(WifiDirect.requiredPermission()),
      notificationPermissionGranted = NotificationManagerCompat.from(context).areNotificationsEnabled(),
      wifiEnabled = ContextCompat.getSystemService(context, WifiManager::class.java)?.isWifiEnabled == true,
      locationEnabled = ContextCompat.getSystemService(context, LocationManager::class.java)?.let { LocationManagerCompat.isLocationEnabled(it) } == true
    )
  }

  fun getMissingPermissions(): List<String> {
    val permissions = buildList {
      add(WifiDirect.requiredPermission())
      if (Build.VERSION.SDK_INT >= 33) {
        add(Manifest.permission.POST_NOTIFICATIONS)
      }
    }

    return permissions.filterNot { isGranted(it) }
  }

  fun startServer() {
    DeviceToDeviceTransferService.startServer(context, TestServerTask(), notificationData(), contentIntent())
  }

  fun startClient(payloadBytes: Long) {
    DeviceToDeviceTransferService.startClient(context, TestClientTask(payloadBytes), notificationData(), contentIntent())
  }

  fun stop() {
    DeviceToDeviceTransferService.stop(context)
  }

  fun setVerified(verified: Boolean) {
    DeviceToDeviceTransferService.setAuthenticationCodeVerified(context, verified)
  }

  fun transferStatuses(): Flow<TransferStatus> = callbackFlow {
    val subscription = EventBus.default.subscribeSticky<TransferStatus> { trySend(it) }
    awaitClose { subscription.unsubscribe() }
  }

  fun taskUpdates(): Flow<TestTaskUpdate> = callbackFlow {
    val subscription = EventBus.default.subscribe<TestTaskUpdate> { trySend(it) }
    awaitClose { subscription.unsubscribe() }
  }

  private fun isGranted(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
  }

  private fun notificationData(): TransferNotificationData {
    return TransferNotificationData(NOTIFICATION_ID, NOTIFICATION_CHANNEL_ID, R.drawable.ic_refresh_20)
  }

  private fun contentIntent(): PendingIntent {
    return PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntentFlags.mutable())
  }
}
