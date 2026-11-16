/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.just
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.GroupManager
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testutil.RecipientTestRule

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class GroupV2UpdateSelfProfileKeyJobTest {

  @get:Rule
  val recipients = RecipientTestRule()

  @Before
  fun setUp() {
    mockkStatic(GroupManager::class)
    every { GroupManager.updateSelfProfileKeyInGroup(any(), any()) } just runs
  }

  @After
  fun tearDown() {
    unmockkStatic(GroupManager::class)
  }

  @Test
  fun run_updatesProfileKeyWhenProfileSharingIsEnabled() {
    val group = recipients.createGroup(recipients.createRecipient("Alice Smith"))
    Recipient.live(group.recipientId).refresh()

    runJob(group.groupId)

    verify(exactly = 1) { GroupManager.updateSelfProfileKeyInGroup(any(), group.groupId) }
  }

  @Test
  fun run_skipsUpdateWhenProfileSharingIsDisabled() {
    val group = recipients.createGroup(recipients.createRecipient("Alice Smith"))
    SignalDatabase.recipients.setProfileSharing(group.recipientId, false)
    Recipient.live(group.recipientId).refresh()

    runJob(group.groupId)

    verify(exactly = 0) { GroupManager.updateSelfProfileKeyInGroup(any(), any()) }
  }

  @Test
  fun run_skipsUpdateWhenGroupIsBlocked() {
    val group = recipients.createGroup(recipients.createRecipient("Alice Smith"))
    SignalDatabase.recipients.setBlocked(group.recipientId, true, 0)
    Recipient.live(group.recipientId).refresh()

    runJob(group.groupId)

    verify(exactly = 0) { GroupManager.updateSelfProfileKeyInGroup(any(), any()) }
  }

  private fun runJob(groupId: GroupId.V2) {
    val job = GroupV2UpdateSelfProfileKeyJob.withoutLimits(groupId)
    job.setContext(ApplicationProvider.getApplicationContext())
    job.run()
  }
}
