/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.BackoffUtil
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.profiles.manage.UsernameRepository
import org.thoughtcrime.securesms.util.FeatureFlags
import kotlin.time.Duration.Companion.days

class ReclaimUsernameAndLinkJob private constructor(parameters: Job.Parameters) : Job(parameters) {
  companion object {
    const val KEY = "UsernameAndLinkRestoreJob"

    private val TAG = Log.tag(ReclaimUsernameAndLinkJob::class.java)
  }

  constructor() : this(
    Parameters.Builder()
      .setQueue(StorageSyncJob.QUEUE_KEY)
      .addConstraint(NetworkConstraint.KEY)
      .setMaxAttempts(Parameters.UNLIMITED)
      .setLifespan(30.days.inWholeMilliseconds)
      .setMaxInstancesForFactory(1)
      .build()
  )

  override fun serialize(): ByteArray? = null

  override fun getFactoryKey(): String = KEY

  override fun run(): Result {
    return when (UsernameRepository.reclaimUsernameIfNecessary()) {
      UsernameRepository.UsernameReclaimResult.SUCCESS -> Result.success()
      UsernameRepository.UsernameReclaimResult.PERMANENT_ERROR -> Result.success()
      UsernameRepository.UsernameReclaimResult.NETWORK_ERROR -> Result.retry(BackoffUtil.exponentialBackoff(runAttempt + 1, FeatureFlags.getDefaultMaxBackoff()))
    }
  }

  override fun onFailure() = Unit

  class Factory : Job.Factory<ReclaimUsernameAndLinkJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): ReclaimUsernameAndLinkJob {
      return ReclaimUsernameAndLinkJob(parameters)
    }
  }
}
