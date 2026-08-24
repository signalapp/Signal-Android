/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.signal.core.util.ThreadUtil
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.jobmanager.Job

/**
 * Clears metadata of existing deleted groups (left and thread deleted). Each group's state is re-validated at run time,
 * so any group that has settled back to active (or regained a thread) is skipped.
 *
 * Clearing a single group is a large multi-table write transaction, so we pause between groups. Otherwise an account with
 * a lot of deleted groups will monopolize the database write connection for as long as it takes to clear them all, which
 * stalls foreground work like message sends and conversation rendering.
 */
class GroupDeletedBackfillWorkerJob private constructor(parameters: Parameters) : Job(parameters) {

  companion object {
    val TAG = Log.tag(GroupDeletedBackfillWorkerJob::class.java)
    const val KEY = "GroupDeletedBackfillWorkerJob"

    private const val PAUSE_BETWEEN_GROUPS_MS = 1000L
  }

  constructor() : this(
    Parameters.Builder()
      .setQueue(KEY)
      .setMaxInstancesForFactory(2)
      .setLifespan(Parameters.IMMORTAL)
      .setMaxAttempts(3)
      .build()
  )

  override fun serialize(): ByteArray? = null

  override fun getFactoryKey(): String = KEY

  override fun run(): Result {
    val groupIdsToClear = SignalDatabase.groups.getGroups().use { groups ->
      groups
        .asSequence()
        .filter { !it.isActive && !SignalDatabase.threads.hasActiveThread(it.recipientId) }
        .map { it.id }
        .toList()
    }

    groupIdsToClear.forEachIndexed { index, id ->
      if (index > 0) {
        ThreadUtil.sleep(PAUSE_BETWEEN_GROUPS_MS)
      }

      SignalDatabase.groups.clearGroupIfLeftAndDeleted(id)
    }

    Log.i(TAG, "Cleared ${groupIdsToClear.size} group(s) during backfill.")
    return Result.success()
  }

  override fun onFailure() = Unit

  class Factory : Job.Factory<GroupDeletedBackfillWorkerJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): GroupDeletedBackfillWorkerJob {
      return GroupDeletedBackfillWorkerJob(parameters)
    }
  }
}
