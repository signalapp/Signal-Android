/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.sample.fcm

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import org.signal.core.util.logging.Log

/**
 * Singleton that receives push challenge tokens from FCM and makes them
 * available to the registration flow.
 */
object PushChallengeReceiver {

  private val TAG = Log.tag(PushChallengeReceiver::class)

  private val challengeFlow = MutableSharedFlow<String>(
    replay = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )

  /**
   * Called by FcmReceiveService when a push challenge is received.
   */
  fun onChallengeReceived(challenge: String) {
    Log.d(TAG, "Push challenge received")
    challengeFlow.tryEmit(challenge)
  }

  /**
   * Suspends until a push challenge token is received.
   * The caller should wrap this in withTimeoutOrNull to handle timeout.
   */
  suspend fun awaitChallenge(): String {
    Log.d(TAG, "Waiting for push challenge...")
    return challengeFlow.first()
  }
}
