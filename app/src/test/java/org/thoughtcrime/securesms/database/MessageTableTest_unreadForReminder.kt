package org.thoughtcrime.securesms.database

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.components.settings.app.notifications.ReminderType
import org.thoughtcrime.securesms.database.model.Mention
import org.thoughtcrime.securesms.mms.IncomingMessage
import org.thoughtcrime.securesms.mms.QuoteModel
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import kotlin.time.Duration.Companion.days

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class MessageTableTest_unreadForReminder {

  @get:Rule
  val recipients = RecipientTestRule()

  private val messages: MessageTable
    get() = SignalDatabase.messages

  @Test
  fun `messages category counts unread messages`() {
    val sender = recipients.createRecipient("Alice Android")
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(sender))

    insertIncoming(threadId, sender, time = 1000)
    insertIncoming(threadId, sender, time = 1001)

    val (count, authors) = messages.getUnreadContentForReminderNotification(threadId, ReminderType.MESSAGES, 1002)

    assertThat(count).isEqualTo(2)
    assertThat(authors).isEqualTo(listOf(sender))
  }

  @Test
  fun `mentions category counts only messages that mention self`() {
    val sender = recipients.createRecipient("Carol Group")
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(sender))

    insertIncoming(threadId, sender, time = 1000)
    insertIncoming(threadId, sender, time = 1001, mentions = listOf(Mention(recipients.self, 0, 1)))

    val (count, authors) = messages.getUnreadContentForReminderNotification(threadId, ReminderType.MENTIONS, 1002)

    assertThat(count).isEqualTo(1)
    assertThat(authors).isEqualTo(listOf(sender))
  }

  @Test
  fun `replies category counts only messages that quote self`() {
    val sender = recipients.createRecipient("Dave Replier")
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(sender))
    val quote = QuoteModel(
      id = 500,
      author = recipients.self,
      text = "original",
      isOriginalMissing = false,
      attachment = null,
      mentions = null,
      type = QuoteModel.Type.NORMAL,
      bodyRanges = null
    )

    insertIncoming(threadId, sender, time = 1000)
    insertIncoming(threadId, sender, time = 1001, quote = quote)

    val (count, authors) = messages.getUnreadContentForReminderNotification(threadId, ReminderType.REPLIES, 1002)

    assertThat(count).isEqualTo(1)
    assertThat(authors).isEqualTo(listOf(sender))
  }

  @Test
  fun `read messages are excluded from the count`() {
    val sender = recipients.createRecipient("Erin Reader")
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(sender))

    val read = insertIncoming(threadId, sender, time = 1000)
    insertIncoming(threadId, sender, time = 1001)
    markRead(read)

    val (count, _) = messages.getUnreadContentForReminderNotification(threadId, ReminderType.MESSAGES, 1002)
    assertThat(count).isEqualTo(1)
  }

  @Test
  fun `only the requested threads are counted`() {
    val included = recipients.createRecipient("Grace Included")
    val excluded = recipients.createRecipient("Heidi Excluded")
    val includedThreadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(included))
    val excludedThreadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(excluded))

    insertIncoming(includedThreadId, included, time = 1000)
    insertIncoming(excludedThreadId, excluded, time = 1000)

    val (count, authors) = messages.getUnreadContentForReminderNotification(includedThreadId, ReminderType.MESSAGES, 1002)

    assertThat(count).isEqualTo(1)
    assertThat(authors).isEqualTo(listOf(included))
  }

  @Test
  fun `author list is most-recent-first, deduped, and capped at 3`() {
    val a = recipients.createRecipient("Author One")
    val b = recipients.createRecipient("Author Two")
    val c = recipients.createRecipient("Author Three")
    val d = recipients.createRecipient("Author Four")
    val group = recipients.createGroup(a, b, c, d)
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(group.recipientId))

    insertIncoming(threadId, a, time = 1000)
    insertIncoming(threadId, b, time = 1001)
    insertIncoming(threadId, b, time = 1002)
    insertIncoming(threadId, c, time = 1003)
    insertIncoming(threadId, d, time = 1004)

    val (count, authors) = messages.getUnreadContentForReminderNotification(threadId, ReminderType.MESSAGES, 1005)

    assertThat(count).isEqualTo(5)
    assertThat(authors).isEqualTo(listOf(d, c, b))
  }

  @Test
  fun `messages more than two weeks old are excluded`() {
    val sender = recipients.createRecipient("Erin Reader")
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(sender))

    insertIncoming(threadId, sender, time = 1000)
    insertIncoming(threadId, sender, time = 1001)

    val (count, _) = messages.getUnreadContentForReminderNotification(threadId, ReminderType.MESSAGES, 1000 + 14.days.inWholeMilliseconds)
    assertThat(count).isEqualTo(1)
  }

  private fun insertIncoming(threadId: Long, from: RecipientId, time: Long, quote: QuoteModel? = null, mentions: List<Mention> = emptyList()): Long {
    val message = IncomingMessage(
      type = MessageType.NORMAL,
      from = from,
      sentTimeMillis = time,
      serverTimeMillis = time,
      receivedTimeMillis = time,
      body = "msg $time",
      quote = quote,
      mentions = mentions
    )
    return messages.insertMessageInbox(message, threadId).get().messageId
  }

  private fun markRead(messageId: Long) {
    SignalDatabase.writableDatabase.execSQL(
      "UPDATE ${MessageTable.TABLE_NAME} SET ${MessageTable.READ} = 1 WHERE ${MessageTable.ID} = ?",
      arrayOf(messageId)
    )
  }
}
