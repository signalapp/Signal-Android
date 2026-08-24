package org.thoughtcrime.securesms.jobs

import android.app.Application
import io.mockk.every
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.database.DraftTable.Draft
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.ReactionRecord
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testutil.RecipientTestRule

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class GroupDeletedBackfillWorkerJobTest {

  @get:Rule
  val recipients = RecipientTestRule()

  @Test
  fun run_clearsLeftGroupWithNoActiveThread() {
    val left = recipients.createGroup(recipients.createRecipient(""))
    SignalDatabase.threads.getOrCreateThreadIdFor(left.recipientId, isGroup = true)
    SignalDatabase.groups.setMember(left.groupId, false)

    GroupDeletedBackfillWorkerJob().run()

    assertFalse(SignalDatabase.groups.getGroup(left.groupId).isPresent)
  }

  @Test
  fun run_clearsTerminatedGroupWithNoActiveThread() {
    val terminated = recipients.createGroup(recipients.createRecipient(""))
    SignalDatabase.groups.setTerminatedBy(terminated.groupId, recipients.self)

    GroupDeletedBackfillWorkerJob().run()

    assertFalse(SignalDatabase.groups.getGroup(terminated.groupId).isPresent)
  }

  @Test
  fun run_leavesActiveGroupUntouched() {
    val active = recipients.createGroup(recipients.createRecipient(""))
    SignalDatabase.threads.getOrCreateThreadIdFor(active.recipientId, isGroup = true)

    GroupDeletedBackfillWorkerJob().run()

    val activeGroup = SignalDatabase.groups.getGroup(active.groupId)
    assertTrue(activeGroup.isPresent)
    assertTrue(activeGroup.get().isActive)
  }

  @Test
  fun run_leavesLeftGroupWithActiveThreadUntouched() {
    val group = recipients.createGroup(recipients.createRecipient(""))
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(group.recipientId, isGroup = true)
    SignalDatabase.threads.markAsActiveEarly(threadId)
    SignalDatabase.groups.setMember(group.groupId, false)

    GroupDeletedBackfillWorkerJob().run()

    val groupRecord = SignalDatabase.groups.getGroup(group.groupId)
    assertTrue(groupRecord.isPresent)
    assertTrue(groupRecord.get().hasV2GroupProperties)
  }

  @Test
  fun run_triggersRecipientIdDatabaseReferenceCascade_forClearedGroupsRecipientOnly() {
    val clearedGroup = recipients.createGroup(recipients.createRecipient(""))
    SignalDatabase.groups.setMember(clearedGroup.groupId, false)

    val keptGroup = recipients.createGroup(recipients.createRecipient(""))
    SignalDatabase.groups.setMember(keptGroup.groupId, true)

    insertReaction(clearedGroup.recipientId)
    insertReaction(keptGroup.recipientId)

    GroupDeletedBackfillWorkerJob().run()

    assertFalse(SignalDatabase.reactions.hasReactions(MessageId(clearedGroup.recipientId.toLong())))
    assertTrue(SignalDatabase.reactions.hasReactions(MessageId(keptGroup.recipientId.toLong())))
  }

  @Test
  fun run_triggersThreadIdDatabaseReferenceCascade_forClearedGroupsThreadOnly() {
    val cleared = recipients.createGroup(recipients.createRecipient(""))
    val clearedThreadId = SignalDatabase.threads.getOrCreateThreadIdFor(cleared.recipientId, isGroup = true)
    SignalDatabase.groups.setMember(cleared.groupId, false)

    val keepRecipientId = recipients.createGroup(recipients.createRecipient(""))
    val keepThreadId = SignalDatabase.threads.getOrCreateThreadIdFor(keepRecipientId.recipientId, isGroup = true)

    SignalDatabase.drafts.replaceDrafts(clearedThreadId, listOf(Draft(type = Draft.TEXT, value = "text")))
    SignalDatabase.drafts.replaceDrafts(keepThreadId, listOf(Draft(type = Draft.TEXT, value = "text")))

    GroupDeletedBackfillWorkerJob().run()

    assertEquals(0, SignalDatabase.drafts.getDrafts(clearedThreadId).count())
    assertEquals(1, SignalDatabase.drafts.getDrafts(keepThreadId).count())
  }

  @Test
  fun run_keepsStubForMultiDeviceLeftGroup() {
    val group = recipients.createGroup(recipients.createRecipient(""))
    SignalDatabase.groups.setMember(group.groupId, false)

    every { recipients.signalStore.account.isMultiDevice } returns true

    GroupDeletedBackfillWorkerJob().run()

    val record = SignalDatabase.groups.getGroup(group.groupId)
    assertTrue(record.isPresent)
    assertFalse(record.get().hasV2GroupProperties)
  }

  private fun insertReaction(id: RecipientId) {
    SignalDatabase.reactions.addReaction(MessageId(id.toLong()), ReactionRecord(emoji = "👍", author = id, dateSent = 1L, dateReceived = 1L))
  }
}
