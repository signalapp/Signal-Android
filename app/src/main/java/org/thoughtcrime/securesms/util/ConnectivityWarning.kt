/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.util

import com.fasterxml.jackson.annotation.JsonProperty
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.keyvalue.SignalStore
import java.io.IOException
import kotlin.time.Duration.Companion.hours

/**
 * An object representing the configuration of the connectivity warning UI, which lets a user know when they haven't been able to connect to the service.
 */
object ConnectivityWarning {

  private val TAG = Log.tag(ConnectivityWarning::class)

  private val config: Config? by lazy {
    try {
      JsonUtils.fromJson(RemoteConfig.connectivityWarningConfig, Config::class.java)
    } catch (e: IOException) {
      Log.w(TAG, "Failed to parse json!", e)
      null
    }
  }

  /** Whether or not connectivity warnings are enabled. */
  val isEnabled
    get() = threshold > 0

  /** If the user has not connected to the service in this amount of time (in ms), then you should show the connectivity warning. A time of <= 0 means never show it. */
  val threshold = config?.thresholdHours?.hours?.inWholeMilliseconds ?: 0

  /** Whether or not you should prompt the user for a log when notifying them that they are unable to connect. */
  val isDebugPromptEnabled: Boolean
    get() {
      val nonNullConfig = config ?: return false

      if (nonNullConfig.percentDebugPrompt == null) {
        return false
      }

      if (nonNullConfig.percentDebugPrompt <= 0f || nonNullConfig.percentDebugPrompt > 100f) {
        return false
      }

      val fraction = nonNullConfig.percentDebugPrompt / 100
      val partsPerMillion = (1_000_000 * fraction).toInt()
      val bucket = BucketingUtil.bucket(RemoteConfig.CRASH_PROMPT_CONFIG, SignalStore.account.aci!!.rawUuid, 1_000_000)

      return partsPerMillion > bucket
    }

  private data class Config(
    @JsonProperty val thresholdHours: Int?,
    @JsonProperty val percentDebugPrompt: Float?
  )
}
