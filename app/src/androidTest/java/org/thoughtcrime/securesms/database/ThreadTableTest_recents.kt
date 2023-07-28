package org.thoughtcrime.securesms.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.CursorUtil
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testing.SignalDatabaseRule
import org.whispersystems.signalservice.api.push.ServiceId.ACI
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
  fun givenARecentRecipient_whenIBlockAndGetRecents_thenIDoNotExpectToSeeThatRecipient() {
    // GIVEN
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(recipient)
    MmsHelper.insert(recipient = recipient, threadId = threadId)
    SignalDatabase.threads.update(threadId, true)

    // WHEN
    SignalDatabase.recipients.setBlocked(recipient.id, true)
    val results: MutableList<RecipientId> = SignalDatabase.threads.getRecentConversationList(10, false, false, false, false, false, false).use { cursor ->
      val ids = mutableListOf<RecipientId>()
      while (cursor.moveToNext()) {
        ids.add(RecipientId.from(CursorUtil.requireLong(cursor, ThreadTable.RECIPIENT_ID)))
      }

      ids
    }

    // THEN
    assertFalse(recipient.id in results)
  }
}
