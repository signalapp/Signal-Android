/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import androidx.annotation.WorkerThread
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.GroupRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.DataRestoreConstraint
import org.thoughtcrime.securesms.jobmanager.impl.DecryptionsDrainedConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.profiles.AvatarHelper

/**
 * Refreshes every group and downloads any missing group avatars after a backup restore, once decryptions have drained.
 * The work is spread over a small number of shared queues so it can't occupy every job runner.
 */
class RefreshGroupsAfterRestoreJob private constructor(parameters: Parameters) : Job(parameters) {

  companion object {
    const val KEY = "RefreshGroupsAfterRestoreJob"

    private val TAG = Log.tag(RefreshGroupsAfterRestoreJob::class)

    private const val IMMEDIATE_AVATAR_COUNT = 30

    /**
     * This job plus avatar downloads for the most recent groups, which do not wait for decryptions to drain.
     */
    @WorkerThread
    fun createRestoreJobs(): List<Job> {
      return listOf(RefreshGroupsAfterRestoreJob()) + createAvatarJobs(getGroupsForRefresh(limit = IMMEDIATE_AVATAR_COUNT))
    }

    private fun getGroupsForRefresh(limit: Int? = null): List<GroupRecord> {
      return SignalDatabase.groups.getV2GroupsForRefresh(includeInactive = !SignalStore.account.isLinkedDevice, limit = limit)
    }

    private fun createAvatarJobs(groups: List<GroupRecord>): List<AvatarGroupsV2DownloadJob> {
      return groups
        .filter { it.requireV2GroupProperties().avatarKey.isNotEmpty() && !AvatarHelper.hasAvatar(AppDependencies.application, it.recipientId) }
        .mapIndexed { index, group -> AvatarGroupsV2DownloadJob.forBulkDownload(group.id.requireV2(), group.requireV2GroupProperties().avatarKey, index) }
    }
  }

  private constructor() : this(
    Parameters.Builder()
      .addConstraint(DecryptionsDrainedConstraint.KEY)
      .addConstraint(DataRestoreConstraint.KEY)
      .setMaxInstancesForFactory(1)
      .setMaxAttempts(Parameters.UNLIMITED)
      .build()
  )

  override fun serialize(): ByteArray? = null

  override fun getFactoryKey(): String = KEY

  override fun run(): Result {
    val groups = getGroupsForRefresh()

    val refreshJobs = groups.mapIndexed { index, group -> RequestGroupV2InfoWorkerJob.forBulkRefresh(group.id.requireV2(), index) }

    val avatarJobs = createAvatarJobs(groups)

    Log.i(TAG, "Refreshing ${refreshJobs.size} groups and downloading ${avatarJobs.size} avatars.")
    AppDependencies.jobManager.addAll(refreshJobs + avatarJobs)

    return Result.success()
  }

  override fun onFailure() = Unit

  class Factory : Job.Factory<RefreshGroupsAfterRestoreJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): RefreshGroupsAfterRestoreJob {
      return RefreshGroupsAfterRestoreJob(parameters)
    }
  }
}
