/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.mockk.verify
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.signal.storageservice.storage.protos.groups.Member
import org.signal.storageservice.storage.protos.groups.local.DecryptedGroup
import org.signal.storageservice.storage.protos.groups.local.DecryptedMember
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class AvatarGroupsV2DownloadJobTest {

  @get:Rule
  val recipients = RecipientTestRule()

  private val application: Application = ApplicationProvider.getApplicationContext()

  @Test
  fun givenAJobForAnOlderAvatarKey_whenIRun_thenIExpectNoDownload() {
    val groupId = insertGroup(avatarKey = "new-key")

    runJob(AvatarGroupsV2DownloadJob(groupId, "old-key"))

    val receiver = AppDependencies.signalServiceMessageReceiver
    verify(exactly = 0) { receiver.retrieveGroupsV2ProfileAvatar(any(), any(), any()) }
  }

  @Test
  fun givenAForcedJobForAnOlderAvatarKey_whenIRun_thenIExpectNoDownload() {
    val groupId = insertGroup(avatarKey = "new-key")

    runJob(AvatarGroupsV2DownloadJob(groupId, "old-key", true))

    val receiver = AppDependencies.signalServiceMessageReceiver
    verify(exactly = 0) { receiver.retrieveGroupsV2ProfileAvatar(any(), any(), any()) }
  }

  @Test
  fun givenAJobForTheCurrentAvatarKey_whenIRun_thenIExpectADownload() {
    val groupId = insertGroup(avatarKey = "current-key")

    runJob(AvatarGroupsV2DownloadJob(groupId, "current-key"))

    val receiver = AppDependencies.signalServiceMessageReceiver
    verify(exactly = 1) { receiver.retrieveGroupsV2ProfileAvatar("current-key", any(), any()) }
  }

  private fun runJob(job: AvatarGroupsV2DownloadJob) {
    job.setContext(application)
    job.run()
  }

  private fun insertGroup(avatarKey: String): GroupId.V2 {
    val decryptedGroup = DecryptedGroup.Builder()
      .title("Group")
      .avatar(avatarKey)
      .members(listOf(DecryptedMember(aciBytes = recipients.selfAci.toByteString(), role = Member.Role.ADMINISTRATOR)))
      .revision(0)
      .build()

    val groupId = SignalDatabase.groups.create(GroupMasterKey(Random.nextBytes(GroupMasterKey.SIZE)), decryptedGroup, null)!!
    val recipientId = SignalDatabase.recipients.getOrInsertFromGroupId(groupId)
    SignalDatabase.recipients.setProfileSharing(recipientId, true)
    Recipient.live(recipientId).refresh()
    return groupId
  }
}
