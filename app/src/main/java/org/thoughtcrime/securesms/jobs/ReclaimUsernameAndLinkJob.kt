/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.BackoffUtil
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.profiles.manage.UsernameRepository
import org.thoughtcrime.securesms.util.RemoteConfig
import kotlin.time.Duration.Companion.days

/**
 * A job to attempt to reclaim a previously-owned username and link after the user re-registers.
 *
 * There's some nuance here in the scheduling -- we need it to after either the account record
 * has been fetched, meaning either [StorageAccountRestoreJob] or [StorageSyncJob] has been run
 * first. We manage this creating chains where the job is constructed and putting it in the same
 * queue as the storage sync job just in case it managed to get enqueued some other way in the
 * future.
 *
 * Also worth noting that [StorageAccountRestoreJob] also attempts to reclaim the username first,
 * but we enqueue this as a fallback since [StorageAccountRestoreJob] has always been a best-effort
 * thing that relies on future [StorageSyncJob]'s to clean up any failures.
 */
class ReclaimUsernameAndLinkJob private constructor(parameters: Parameters) : Job(parameters) {
  companion object {
    const val KEY = "UsernameAndLinkRestoreJob"
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
      UsernameRepository.UsernameReclaimResult.NETWORK_ERROR -> Result.retry(BackoffUtil.exponentialBackoff(runAttempt + 1, RemoteConfig.defaultMaxBackoff))
    }
  }

  override fun onFailure() = Unit

  class Factory : Job.Factory<ReclaimUsernameAndLinkJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): ReclaimUsernameAndLinkJob {
      return ReclaimUsernameAndLinkJob(parameters)
    }
  }
}
