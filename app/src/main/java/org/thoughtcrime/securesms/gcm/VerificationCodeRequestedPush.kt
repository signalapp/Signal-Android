/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.gcm

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.signal.core.util.logging.Log

@Serializable
data class VerificationCodeRequestedPush(val timestamp: Long?) {
  companion object {

    private val TAG = Log.tag(VerificationCodeRequestedPush::class)

    private val json = Json { ignoreUnknownKeys = true }

    @JvmStatic
    fun fromJson(jsonString: String): VerificationCodeRequestedPush? {
      return try {
        json.decodeFromString(jsonString)
      } catch (e: Throwable) {
        Log.w(TAG, "Unable to parse verification code request", e)
        null
      }
    }
  }
}
