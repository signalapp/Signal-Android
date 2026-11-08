package org.thoughtcrime.securesms.jobs

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.components.settings.app.notifications.ReminderType

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class UnreadReminderJobTest {

  private lateinit var context: Context

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun `messages, one author, uses the singular name only`() {
    val result = UnreadReminderJob.create().buildAuthorSummary(context, ReminderType.MESSAGES, 1, listOf("Alice"))
    assertEquals("Alice", result)
  }

  @Test
  fun `messages, two authors, joins both names`() {
    val result = UnreadReminderJob.create().buildAuthorSummary(context, ReminderType.MESSAGES, 5, listOf("Alice", "Bob"))
    assertEquals("Alice and Bob", result)
  }

  @Test
  fun `messages, three or more authors, uses the first name and others`() {
    val result = UnreadReminderJob.create().buildAuthorSummary(context, ReminderType.MESSAGES, 7, listOf("Alice", "Bob", "Carol"))
    assertEquals("Alice and others", result)
  }

  @Test
  fun `mentions, one author, omits the count`() {
    val result = UnreadReminderJob.create().buildAuthorSummary(context, ReminderType.MENTIONS, 1, listOf("Dave"))
    assertEquals("a mention of you by Dave", result)
  }

  @Test
  fun `mentions, two authors, joins both names`() {
    val result = UnreadReminderJob.create().buildAuthorSummary(context, ReminderType.MENTIONS, 2, listOf("Dave", "Erin"))
    assertEquals("2 mentions of you by Dave and Erin", result)
  }

  @Test
  fun `mentions, three or more authors, uses the first name and others`() {
    val result = UnreadReminderJob.create().buildAuthorSummary(context, ReminderType.MENTIONS, 9, listOf("Dave", "Erin", "Frank"))
    assertEquals("9 mentions of you by Dave and others", result)
  }

  @Test
  fun `replies, one author, omits the count`() {
    val result = UnreadReminderJob.create().buildAuthorSummary(context, ReminderType.REPLIES, 1, listOf("Grace"))
    assertEquals("a reply from Grace", result)
  }

  @Test
  fun `replies, two authors, joins both names`() {
    val result = UnreadReminderJob.create().buildAuthorSummary(context, ReminderType.REPLIES, 2, listOf("Grace", "Heidi"))
    assertEquals("2 replies from Grace and Heidi", result)
  }

  @Test
  fun `replies, three or more authors, uses the first name and others`() {
    val result = UnreadReminderJob.create().buildAuthorSummary(context, ReminderType.REPLIES, 4, listOf("Grace", "Heidi", "Ivan"))
    assertEquals("4 replies from Grace and others", result)
  }

  @Test
  fun `nothing unread produces an empty summary`() {
    val result = UnreadReminderJob.create().buildSummary(context)
    assertEquals("", result)
  }

  @Test
  fun `messages only, singular message and singular author`() {
    val result = UnreadReminderJob.create().buildSummary(context, messages = 1, messageAuthors = listOf("Alice"))
    assertEquals("You have 1 unread message from Alice.", result)
  }

  @Test
  fun `messages only, plural messages and two authors`() {
    val result = UnreadReminderJob.create().buildSummary(context, messages = 5, messageAuthors = listOf("Alice", "Bob"))
    assertEquals("You have 5 unread messages from Alice and Bob.", result)
  }

  @Test
  fun `messages and mentions, combines counts and authors`() {
    val result = UnreadReminderJob.create().buildSummary(
      context,
      messages = 1,
      mentions = 1,
      messageAuthors = listOf("Alice"),
      mentionAuthors = listOf("Dave")
    )
    assertEquals("You have 1 unread message, including a mention of you by Dave.", result)
  }

  @Test
  fun `messages and replies, combines counts and authors`() {
    val result = UnreadReminderJob.create().buildSummary(
      context,
      messages = 1,
      replies = 1,
      messageAuthors = listOf("Alice"),
      replyAuthors = listOf("Grace")
    )
    assertEquals("You have 1 unread message, including a reply from Grace.", result)
  }

  @Test
  fun `messages, mentions, and replies all true uses the full summary`() {
    val result = UnreadReminderJob.create().buildSummary(
      context,
      messages = 1,
      mentions = 1,
      replies = 1,
      messageAuthors = listOf("Alice"),
      mentionAuthors = listOf("Dave"),
      replyAuthors = listOf("Grace")
    )
    assertEquals("You have 1 unread message, including a mention of you by Dave and a reply from Grace.", result)
  }

  @Test
  fun `messages, mentions, and replies all true, plural messages`() {
    val result = UnreadReminderJob.create().buildSummary(
      context,
      messages = 3,
      mentions = 1,
      replies = 1,
      messageAuthors = listOf("Alice", "Bob"),
      mentionAuthors = listOf("Dave"),
      replyAuthors = listOf("Grace")
    )
    assertEquals("You have 3 unread messages, including a mention of you by Dave and a reply from Grace.", result)
  }

  @Test
  fun `messages, mentions, and replies all true, two mention and reply authors`() {
    val result = UnreadReminderJob.create().buildSummary(
      context,
      messages = 1,
      mentions = 2,
      replies = 2,
      messageAuthors = listOf("Alice"),
      mentionAuthors = listOf("Dave", "Erin"),
      replyAuthors = listOf("Grace", "Heidi")
    )
    assertEquals("You have 1 unread message, including 2 mentions of you by Dave and Erin and 2 replies from Grace and Heidi.", result)
  }

  @Test
  fun `messages, mentions, and replies all true, three or more mention and reply authors`() {
    val result = UnreadReminderJob.create().buildSummary(
      context,
      messages = 1,
      mentions = 5,
      replies = 4,
      messageAuthors = listOf("Alice"),
      mentionAuthors = listOf("Dave", "Erin", "Frank"),
      replyAuthors = listOf("Grace", "Heidi", "Ivan")
    )
    assertEquals("You have 1 unread message, including 5 mentions of you by Dave and others and 4 replies from Grace and others.", result)
  }

  @Test
  fun `hideAuthors, messages only, omits author names`() {
    val result = UnreadReminderJob.create().buildSummary(context, messages = 1, messageAuthors = listOf("Alice"), hideAuthors = true)
    assertEquals("You have 1 unread message.", result)
  }

  @Test
  fun `hideAuthors, messages and mentions, mentions are dropped entirely`() {
    val result = UnreadReminderJob.create().buildSummary(context, messages = 2, mentions = 2, messageAuthors = listOf("Alice"), mentionAuthors = listOf("Frank", "Erin"), hideAuthors = true)
    assertEquals("You have 2 unread messages.", result)
  }

  @Test
  fun `hideAuthors, messages and replies, replies are dropped entirely`() {
    val result = UnreadReminderJob.create().buildSummary(context, messages = 1, replies = 3, messageAuthors = listOf("Alice"), replyAuthors = listOf("Grace"), hideAuthors = true)
    assertEquals("You have 1 unread message.", result)
  }

  @Test
  fun `hideAuthors, nothing unread produces an empty summary`() {
    val result = UnreadReminderJob.create().buildSummary(context, hideAuthors = true)
    assertEquals("", result)
  }
}
