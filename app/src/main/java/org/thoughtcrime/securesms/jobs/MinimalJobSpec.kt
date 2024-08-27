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
  val priority: Int,
  val isRunning: Boolean,
  val isMemoryOnly: Boolean
)
