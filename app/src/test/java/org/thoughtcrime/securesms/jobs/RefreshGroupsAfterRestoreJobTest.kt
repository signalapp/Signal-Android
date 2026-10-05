/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import io.mockk.every
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.update
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.signal.storageservice.storage.protos.groups.Member
import org.signal.storageservice.storage.protos.groups.local.DecryptedGroup
import org.signal.storageservice.storage.protos.groups.local.DecryptedMember
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.ThreadTable
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.profiles.AvatarHelper
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class RefreshGroupsAfterRestoreJobTest {

  @get:Rule
  val recipients = RecipientTestRule()

  private val application: Application = ApplicationProvider.getApplicationContext()
  private val enqueuedJobs = mutableListOf<Job>()

  @Before
  fun setUp() {
    every { AppDependencies.jobManager.addAll(any<List<Job>>()) } answers { enqueuedJobs += firstArg<List<Job>>() }
  }

  @Test
  fun givenGroups_whenICreateRestoreJobs_thenIExpectTheRefreshJobAndAvatarsForTheMostRecentGroups() {
    val groups = (1..35).map { insertGroup(avatarKey = "key-$it", threadDate = it * 1000L) }
    val mostRecentFirst = groups.reversed()

    val jobs = RefreshGroupsAfterRestoreJob.createRestoreJobs()

    assertThat(jobs.filterIsInstance<RefreshGroupsAfterRestoreJob>()).hasSize(1)
    val avatarJobs = jobs.filterIsInstance<AvatarGroupsV2DownloadJob>()
    assertThat(avatarJobs.map { it.groupId() }).isEqualTo(mostRecentFirst.take(30))
    assertThat(avatarJobs.map { it.parameters.queue }).isEqualTo(alternatingQueues("AvatarGroupsV2DownloadJob_Bulk_", 30))
  }

  @Test
  fun givenGroupsWithoutAvatars_whenICreateRestoreJobs_thenIExpectNoAvatarJobs() {
    insertGroup(avatarKey = "", threadDate = 1000L)

    val jobs = RefreshGroupsAfterRestoreJob.createRestoreJobs()

    assertThat(jobs.filterIsInstance<AvatarGroupsV2DownloadJob>()).isEmpty()
  }

  @Test
  fun givenGroups_whenIRun_thenIExpectRefreshesForEveryGroupInRecencyOrderAcrossTwoQueues() {
    val groups = (1..5).map { insertGroup(avatarKey = "", threadDate = it * 1000L) }

    runJob()

    val refreshJobs = enqueuedJobs.filterIsInstance<RequestGroupV2InfoWorkerJob>()
    assertThat(refreshJobs.map { it.groupId() }).isEqualTo(groups.reversed())
    assertThat(refreshJobs.map { it.parameters.queue }).isEqualTo(alternatingQueues("RequestGroupV2InfoWorkerJob_Bulk_", 5))
  }

  @Test
  fun givenGroupsWithAndWithoutLocalAvatars_whenIRun_thenIExpectAvatarJobsOnlyForMissingAvatars() {
    val downloaded = insertGroup(avatarKey = "key-1", threadDate = 3000L)
    val missing = insertGroup(avatarKey = "key-2", threadDate = 2000L)
    insertGroup(avatarKey = "", threadDate = 1000L)
    AvatarHelper.getAvatarFile(application, recipientIdFor(downloaded)).apply {
      parentFile?.mkdirs()
      writeBytes(byteArrayOf(1, 2, 3))
    }

    runJob()

    assertThat(enqueuedJobs.filterIsInstance<AvatarGroupsV2DownloadJob>().map { it.groupId() }).containsExactly(missing)
  }

  @Test
  fun givenALinkedDevice_whenIRun_thenIExpectInactiveGroupsSkipped() {
    every { recipients.signalStore.account.isLinkedDevice } returns true
    val active = insertGroup(avatarKey = "", threadDate = 2000L)
    val left = insertGroup(avatarKey = "", threadDate = 1000L)
    SignalDatabase.groups.setMember(left, false)

    runJob()

    assertThat(enqueuedJobs.filterIsInstance<RequestGroupV2InfoWorkerJob>().map { it.groupId() }).containsExactly(active)
  }

  private fun runJob() {
    val job = RefreshGroupsAfterRestoreJob.createRestoreJobs().filterIsInstance<RefreshGroupsAfterRestoreJob>().single()
    job.setContext(application)
    job.run()
  }

  private fun insertGroup(avatarKey: String, threadDate: Long): GroupId.V2 {
    val decryptedGroup = DecryptedGroup.Builder()
      .title("Group")
      .avatar(avatarKey)
      .members(listOf(DecryptedMember(aciBytes = recipients.selfAci.toByteString(), role = Member.Role.ADMINISTRATOR)))
      .revision(0)
      .build()

    val groupId = SignalDatabase.groups.create(GroupMasterKey(Random.nextBytes(GroupMasterKey.SIZE)), decryptedGroup, null)!!
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(recipientIdFor(groupId)))
    SignalDatabase.threads.writableDatabase
      .update(ThreadTable.TABLE_NAME)
      .values(ThreadTable.DATE to threadDate)
      .where("${ThreadTable.ID} = ?", threadId)
      .run()

    return groupId
  }

  private fun recipientIdFor(groupId: GroupId) = SignalDatabase.recipients.getByGroupId(groupId).get()

  private fun Job.groupId(): GroupId.V2 {
    return GroupId.parseOrThrow(JsonJobData.deserialize(serialize()).getString("group_id")).requireV2()
  }

  private fun alternatingQueues(prefix: String, count: Int): List<String> {
    return (0 until count).map { "$prefix${it % 2}" }
  }
}
