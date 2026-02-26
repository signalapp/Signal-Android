/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.messages

import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.signal.libsignal.zkgroup.groups.GroupSecretParams
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.GroupRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.groupMasterKey
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.hasGroupContext
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.internal.push.DataMessage
import java.util.Optional

/**
 * A caching system for batch processing of incoming messages.
 *
 * The primary things that enables the cache to safely store various group state:
 * 1. [IncomingMessageObserver] holds a group processing lock during a batch process preventing group state from changing.
 * Helps enable [groupRevisionCache] and [groupRecordCache].
 *
 * 2. Some group state doesn't change as it's derived from the [GroupMasterKey]. Enables [groupSecretParamsAndIdCache].
 */
abstract class BatchCache {
  companion object {
    const val BATCH_SIZE = 30
  }

  abstract val batchThreadUpdates: Boolean

  val groupQueueEmptyCache = HashSet<String>(BATCH_SIZE)
  val groupRevisionCache = HashMap<GroupId, Int>(BATCH_SIZE)
  val groupRecordCache = HashMap<GroupId.V2, Optional<GroupRecord>>(BATCH_SIZE)

  protected val groupSecretParamsAndIdCache = HashMap<GroupMasterKey, Pair<GroupSecretParams, GroupId.V2>>(BATCH_SIZE)

  fun getGroupInfo(message: DataMessage): Pair<GroupSecretParams?, GroupId.V2?> {
    return if (message.hasGroupContext) {
      groupSecretParamsAndIdCache.getOrPut(message.groupV2!!.groupMasterKey) {
        val params = GroupSecretParams.deriveFromMasterKey(message.groupV2!!.groupMasterKey)
        params to GroupId.v2(params.publicParams.groupIdentifier)
      }
    } else {
      null to null
    }
  }

  open fun flushAndClear() {
    groupQueueEmptyCache.clear()
    groupRevisionCache.clear()
    groupRecordCache.clear()
    groupSecretParamsAndIdCache.clear()
  }

  protected fun flushJob(job: Job) {
    AppDependencies.jobManager.add(job)
  }

  protected fun flushIncomingMessageInsertThreadUpdate(threadId: Long) {
    SignalDatabase.threads.updateForMessageInsert(threadId, unarchive = true)
  }

  protected fun flushMslDelete(recipientId: RecipientId, device: Int, timestamps: List<Long>) {
    SignalDatabase.messageLog.deleteEntriesForRecipient(timestamps, recipientId, device)
  }

  abstract fun addJob(job: Job)
  abstract fun addIncomingMessageInsertThreadUpdate(threadId: Long)
  abstract fun addMslDelete(recipientId: RecipientId, device: Int, timestamps: List<Long>)
}

/**
 * This is intended to be used when processing messages outside of [IncomingMessageObserver] where
 * no batching is possible, mostly when the [org.thoughtcrime.securesms.jobs.PushProcessMessageJob] runs.
 */
class OneTimeBatchCache : BatchCache() {
  override val batchThreadUpdates: Boolean = false

  override fun addJob(job: Job) {
    flushJob(job)
  }

  override fun addIncomingMessageInsertThreadUpdate(threadId: Long) {
    flushIncomingMessageInsertThreadUpdate(threadId)
  }

  override fun addMslDelete(recipientId: RecipientId, device: Int, timestamps: List<Long>) {
    flushMslDelete(recipientId, device, timestamps)
  }
}

/**
 * This is intended to be used in [IncomingMessageObserver] to batch jobs (e.g., [org.thoughtcrime.securesms.jobs.SendDeliveryReceiptJob])
 * and dedupe and batch calls to [SignalDatabase.threads.updateForMessageInsert].
 *
 * Why Jobs? There's a lot of locking and database management when adding a job. Delaying that work from the processing loop
 * and doing it all at once reduces the number of times we need to do either, reducing overall contention.
 *
 * Why thread updates? Thread updating has always been the longest thing to do in message processing. Deduping allows
 * us to only call it once per thread in a batch instead of X times a message for that thread is in the batch.
 */
class ReusedBatchCache : BatchCache() {
  override val batchThreadUpdates: Boolean = true

  private val batchedJobs = ArrayList<Job>(BATCH_SIZE)
  private val threadUpdates = HashSet<Long>(BATCH_SIZE)
  private val mslDeletes = HashMap<Pair<RecipientId, Int>, MutableList<Long>>(BATCH_SIZE)

  override fun addJob(job: Job) {
    batchedJobs += job
  }

  override fun addIncomingMessageInsertThreadUpdate(threadId: Long) {
    threadUpdates += threadId
  }

  override fun addMslDelete(recipientId: RecipientId, device: Int, timestamps: List<Long>) {
    mslDeletes.getOrPut(recipientId to device) { mutableListOf() } += timestamps
  }

  override fun flushAndClear() {
    super.flushAndClear()

    if (batchedJobs.isNotEmpty()) {
      AppDependencies.jobManager.addAll(batchedJobs)
    }
    batchedJobs.clear()

    if (threadUpdates.isNotEmpty()) {
      SignalDatabase.runInTransaction {
        threadUpdates.forEach { flushIncomingMessageInsertThreadUpdate(it) }
      }
    }
    threadUpdates.clear()

    if (mslDeletes.isNotEmpty()) {
      SignalDatabase.runInTransaction {
        mslDeletes.forEach { (key, timestamps) -> flushMslDelete(key.first, key.second, timestamps) }
      }
    }
    mslDeletes.clear()
  }
}
