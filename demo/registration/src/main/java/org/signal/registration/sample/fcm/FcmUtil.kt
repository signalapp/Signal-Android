/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.sample.fcm

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import org.signal.core.util.logging.Log

/**
 * Utility functions for Firebase Cloud Messaging.
 */
object FcmUtil {

  private val TAG = Log.tag(FcmUtil::class)

  /**
   * Retrieves the FCM registration token if available.
   * Returns null if FCM is not available on this device.
   *
   * @param context Application context needed to initialize Firebase
   */
  suspend fun getToken(context: Context): String? {
    return try {
      FirebaseApp.initializeApp(context)

      val token = FirebaseMessaging.getInstance().token.await()
      Log.d(TAG, "FCM token retrieved successfully")
      token
    } catch (e: Exception) {
      Log.w(TAG, "Failed to get FCM token", e)
      null
    }
  }

  /**
   * Checks if Google Play Services is available on this device.
   */
  fun isPlayServicesAvailable(context: Context): Boolean {
    val availability = GoogleApiAvailability.getInstance()
    val resultCode = availability.isGooglePlayServicesAvailable(context)
    return resultCode == ConnectionResult.SUCCESS
  }
}
