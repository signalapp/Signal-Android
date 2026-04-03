/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.AutoDownloadEmojiConstraint
import org.thoughtcrime.securesms.jobmanager.impl.BackoffUtil
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.service.webrtc.CallingAssets
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Job that downloads missing calling assets.
 */
class CallingAssetsDownloadJob private constructor(parameters: Parameters) : Job(parameters) {
  companion object {
    private val TAG = Log.tag(CallingAssetsDownloadJob::class)

    const val KEY = "CallingAssetsDownloadJob"
  }

  constructor() : this(
    Parameters.Builder()
      .addConstraint(AutoDownloadEmojiConstraint.KEY)
      .setLifespan(3.days.inWholeMilliseconds)
      .setMaxAttempts(5)
      .setMaxInstancesForFactory(1)
      .build()
  )

  override fun serialize(): ByteArray? = null

  override fun getFactoryKey(): String = KEY

  override fun run(): Result {
    var succeeded = true
    if (SignalStore.misc.callingAssetsVersion != CallingAssets.CURRENT_VERSION) {
      succeeded = CallingAssets.downloadMissingAssets()
    }

    CallingAssets.registerAssetsIfNeeded()

    if (!succeeded) {
      Log.w(TAG, "Failed to download some calling assets")
      return Result.retry(BackoffUtil.exponentialBackoff(runAttempt + 1, 1.hours.inWholeMilliseconds))
    }
    SignalStore.misc.callingAssetsVersion = CallingAssets.CURRENT_VERSION
    return Result.success()
  }

  override fun onFailure() = Unit

  class Factory : Job.Factory<CallingAssetsDownloadJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): CallingAssetsDownloadJob {
      return CallingAssetsDownloadJob(parameters)
    }
  }
}
