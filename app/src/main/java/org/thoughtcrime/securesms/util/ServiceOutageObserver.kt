/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.util

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.jobs.ServiceOutageDetectionJob
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * A lifecycle aware observer that can be instantiated to monitor the service for outages.
 *
 * @see [ServiceOutageDetectionJob]
 */
class ServiceOutageObserver(private val context: Context) : DefaultLifecycleObserver {
  companion object {
    val TAG = Log.tag(ServiceOutageObserver::class)
  }

  private var observing = false

  override fun onResume(owner: LifecycleOwner) {
    observing = true
  }

  override fun onStop(owner: LifecycleOwner) {
    observing = false
  }

  val flow: Flow<Boolean> = flow {
    emit(TextSecurePreferences.getServiceOutage(context))
    TextSecurePreferences.setLastOutageCheckTime(context, System.currentTimeMillis())

    while (true) {
      if (observing && getNextCheckTime(context) <= System.currentTimeMillis()) {
        when (queryAvailability()) {
          Result.SUCCESS -> {
            TextSecurePreferences.setServiceOutage(context, false)
            emit(false)
          }

          Result.FAILURE -> {
            Log.w(TAG, "Service is down.")
            TextSecurePreferences.setServiceOutage(context, true)
            emit(true)
          }

          Result.RETRY_LATER -> {
            Log.w(TAG, "Service status check returned an unrecognized IP address. Could be a weird network state. Prompting retry.")
          }
        }
      }

      val nextCheckTime = getNextCheckTime(context)
      val now = System.currentTimeMillis()
      val delay = nextCheckTime - now
      if (delay > 0) {
        delay(delay)
      }
    }
  }

  private fun getNextCheckTime(context: Context): Long = TextSecurePreferences.getLastOutageCheckTime(context) + ServiceOutageDetectionJob.CHECK_TIME

  private suspend fun queryAvailability(): Result = withContext(Dispatchers.IO) {
    try {
      val address = InetAddress.getByName(BuildConfig.SIGNAL_SERVICE_STATUS_URL)

      val now = System.currentTimeMillis()
      TextSecurePreferences.setLastOutageCheckTime(context, now)

      if (ServiceOutageDetectionJob.IP_SUCCESS == address.hostAddress) {
        Result.SUCCESS
      } else if (ServiceOutageDetectionJob.IP_FAILURE == address.hostAddress) {
        Result.FAILURE
      } else {
        Result.RETRY_LATER
      }
    } catch (e: UnknownHostException) {
      Log.i(TAG, "Received UnknownHostException!", e)
      Result.RETRY_LATER
    }
  }

  private enum class Result {
    SUCCESS, FAILURE, RETRY_LATER
  }
}
