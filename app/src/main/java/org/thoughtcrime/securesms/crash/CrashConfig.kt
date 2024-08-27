/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.crash

import androidx.annotation.VisibleForTesting
import com.fasterxml.jackson.annotation.JsonProperty
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.BucketingUtil
import org.thoughtcrime.securesms.util.JsonUtils
import org.thoughtcrime.securesms.util.RemoteConfig
import org.whispersystems.signalservice.api.push.ServiceId
import java.io.IOException

object CrashConfig {

  private val TAG = Log.tag(CrashConfig::class.java)

  /**
   * A list of patterns for crashes we'd like to find matches for in the crash database.
   */
  val patterns: List<CrashPattern> by lazy { computePatterns() }

  @VisibleForTesting
  fun computePatterns(): List<CrashPattern> {
    val aci: ServiceId.ACI = SignalStore.account.aci ?: return emptyList()

    val serialized = RemoteConfig.crashPromptConfig
    if (serialized.isNullOrBlank()) {
      return emptyList()
    }

    if (SignalStore.account.aci == null) {
      return emptyList()
    }

    val list: List<Config> = try {
      JsonUtils.fromJsonArray(serialized, Config::class.java)
    } catch (e: IOException) {
      Log.w(TAG, "Failed to parse json!", e)
      emptyList()
    }

    return list
      .asSequence()
      .filter { it.rolledOutToLocalUser(aci) }
      .map {
        if (it.name?.isBlank() == true) {
          it.copy(name = null)
        } else {
          it
        }
      }
      .map {
        if (it.message ?.isBlank() == true) {
          it.copy(message = null)
        } else {
          it
        }
      }
      .map {
        if (it.stackTrace ?.isBlank() == true) {
          it.copy(stackTrace = null)
        } else {
          it
        }
      }
      .filter { it.name != null || it.message != null || it.stackTrace != null }
      .map {
        CrashPattern(
          namePattern = it.name,
          messagePattern = it.message,
          stackTracePattern = it.stackTrace
        )
      }
      .toList()
  }

  /**
   * Represents a pattern for a crash we're interested in prompting the user about. In this context, "pattern" means
   * a case-sensitive substring of a larger string. So "IllegalArgument" would match "IllegalArgumentException".
   * Not a regex or anything.
   *
   * One of the fields is guaranteed to be set.
   *
   * @param namePattern A possible substring of an exception name we're looking for in the crash table.
   * @param messagePattern A possible substring of an exception message we're looking for in the crash table.
   */
  data class CrashPattern(
    val namePattern: String? = null,
    val messagePattern: String? = null,
    val stackTracePattern: String? = null
  ) {
    init {
      check(namePattern != null || messagePattern != null || stackTracePattern != null)
    }
  }

  private data class Config(
    @JsonProperty val name: String?,
    @JsonProperty val message: String?,
    @JsonProperty val stackTrace: String?,
    @JsonProperty val percent: Float?
  ) {

    /** True if the local user is contained within the percent rollout, otherwise false. */
    fun rolledOutToLocalUser(aci: ServiceId.ACI): Boolean {
      if (percent == null) {
        return false
      }

      if (percent <= 0f || percent > 100f) {
        return false
      }

      val fraction = percent / 100
      val partsPerMillion = (1_000_000 * fraction).toInt()
      val bucket = BucketingUtil.bucket(RemoteConfig.CRASH_PROMPT_CONFIG, aci.rawUuid, 1_000_000)
      return partsPerMillion > bucket
    }
  }
}
