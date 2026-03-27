package org.thoughtcrime.securesms.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.models.ServiceId.ACI
import org.signal.core.util.CursorUtil
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testing.SignalDatabaseRule
import java.util.UUID

@Suppress("ClassName")
@RunWith(AndroidJUnit4::class)
class ThreadTableTest_recents {

  @Rule
  @JvmField
  val databaseRule = SignalDatabaseRule()

  private lateinit var recipient: Recipient

  @Before
  fun setUp() {
    recipient = Recipient.resolved(SignalDatabase.recipients.getOrInsertFromServiceId(ACI.from(UUID.randomUUID())))
  }

  @Test
  fun getRecentConversationList_excludes_blocked_recipients() {
    createActiveThreadFor(recipient)

    SignalDatabase.recipients.setBlocked(recipient.id, true)

    assertFalse(recipient.id in getRecentConversationRecipients(limit = 10))
  }

  @Test
  fun getRecentConversationList_excludes_hidden_recipients() {
    createActiveThreadFor(recipient)

    SignalDatabase.recipients.markHidden(recipient.id)

    assertFalse(recipient.id in getRecentConversationRecipients(limit = 10))
  }

  private fun createActiveThreadFor(recipient: Recipient) {
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(recipient)
    MmsHelper.insert(recipient = recipient, threadId = threadId)
    SignalDatabase.threads.update(threadId, true)
  }

  @Suppress("SameParameterValue")
  private fun getRecentConversationRecipients(limit: Int = 10): Set<RecipientId> {
    return SignalDatabase.threads
      .getRecentConversationList(limit = limit, includeInactiveGroups = false, individualsOnly = false, groupsOnly = false, hideV1Groups = false, hideSms = false, hideSelf = false)
      .use { cursor ->
        buildSet {
          while (cursor.moveToNext()) {
            add(RecipientId.from(CursorUtil.requireLong(cursor, ThreadTable.RECIPIENT_ID)))
          }
        }
      }
  }
}
