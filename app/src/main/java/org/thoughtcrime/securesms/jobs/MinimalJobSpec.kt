/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

/**
 * A smaller version of [org.thoughtcrime.securesms.jobmanager.persistence.JobSpec] that contains on the the data we need
 * to sort and pick jobs in [FastJobStorage].
 */
data class MinimalJobSpec(
  val id: String,
  val factoryKey: String,
  val queueKey: String?,
  val createTime: Long,
  val lastRunAttemptTime: Long,
  val nextBackoffInterval: Long,
  val globalPriority: Int,
  val queuePriority: Int,
  val isRunning: Boolean,
  val isMemoryOnly: Boolean,
  val initialDelay: Long
) {
  override fun toString(): String {
    return "MinimalJobSpec(id=JOB::$id, factoryKey=$factoryKey, queueKey=$queueKey, createTime=$createTime, lastRunAttemptTime=$lastRunAttemptTime, nextBackoffInterval=$nextBackoffInterval, globalPriority=$globalPriority, queuePriority=$queuePriority, isRunning=$isRunning, isMemoryOnly=$isMemoryOnly, initialDelay=$initialDelay)"
  }
}
