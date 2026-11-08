/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.messages

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JobManager
import org.thoughtcrime.securesms.jobs.GroupCallPeekJob
import org.thoughtcrime.securesms.jobs.protos.GroupCallPeekJobData

class ReusedBatchCacheTest {

  private val addedJobs = mutableListOf<Job>()

  @Before
  fun setUp() {
    val jobManager = mockk<JobManager>(relaxed = true) {
      every { add(any()) } answers { addedJobs += firstArg<Job>() }
    }
    mockkStatic(AppDependencies::class)
    every { AppDependencies.jobManager } returns jobManager
  }

  @After
  fun tearDown() {
    unmockkStatic(AppDependencies::class)
  }

  @Test
  fun givenMultipleGroupCallPeeksPerGroup_whenIFlush_thenIExpectOneJobPerGroupWithTheLatestData() {
    val cache = ReusedBatchCache()

    cache.addGroupCallPeek(GroupCallPeekJobData(groupRecipientId = 1, senderRecipientId = 10, serverTimestamp = 100))
    cache.addGroupCallPeek(GroupCallPeekJobData(groupRecipientId = 2, senderRecipientId = 20, serverTimestamp = 200))
    cache.addGroupCallPeek(GroupCallPeekJobData(groupRecipientId = 1, senderRecipientId = 11, serverTimestamp = 300))

    assertThat(addedJobs).isEmpty()

    cache.flushAndClear()

    assertThat(addedJobs.map { GroupCallPeekJobData.ADAPTER.decode((it as GroupCallPeekJob).serialize()) }).containsExactlyInAnyOrder(
      GroupCallPeekJobData(groupRecipientId = 1, senderRecipientId = 11, serverTimestamp = 300),
      GroupCallPeekJobData(groupRecipientId = 2, senderRecipientId = 20, serverTimestamp = 200)
    )
  }

  @Test
  fun givenFlushedGroupCallPeeks_whenIFlushAgain_thenIExpectNoNewJobs() {
    val cache = ReusedBatchCache()
    cache.addGroupCallPeek(GroupCallPeekJobData(groupRecipientId = 1, senderRecipientId = 10, serverTimestamp = 100))
    cache.flushAndClear()
    addedJobs.clear()

    cache.flushAndClear()

    assertThat(addedJobs).isEmpty()
  }
}
