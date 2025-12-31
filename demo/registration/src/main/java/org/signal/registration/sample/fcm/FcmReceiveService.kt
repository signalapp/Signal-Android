/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.sample.fcm

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.signal.core.util.logging.Log

/**
 * Firebase Cloud Messaging service for receiving push notifications.
 * During registration, this is used to receive push challenge tokens from the server.
 */
class FcmReceiveService : FirebaseMessagingService() {

  companion object {
    private val TAG = Log.tag(FcmReceiveService::class)
  }

  override fun onMessageReceived(remoteMessage: RemoteMessage) {
    Log.d(TAG, "onMessageReceived: ${remoteMessage.messageId}")

    val challenge = remoteMessage.data["challenge"]
    if (challenge != null) {
      Log.d(TAG, "Received push challenge")
      PushChallengeReceiver.onChallengeReceived(challenge)
    }
  }

  override fun onNewToken(token: String) {
    Log.d(TAG, "onNewToken")
  }
}
