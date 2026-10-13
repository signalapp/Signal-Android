
package org.thoughtcrime.securesms.database

import android.app.Application
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.every
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import kotlin.time.Duration.Companion.days

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class ThreadTableTest_mutedThreadIds {

  @get:Rule
  val recipients = RecipientTestRule()

  private lateinit var contactId: RecipientId
  private var threadId: Long = 0

  @Before
  fun setUp() {
    contactId = recipients.createRecipient("Alice Android")
    threadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(contactId))
    SignalDatabase.threads.markAsActiveEarly(threadId)
    SignalDatabase.recipients.setMuted(contactId, Long.MAX_VALUE)
  }

  private fun globalDefaults(unreadReminder: Boolean = true, mentions: Boolean = true, replies: Boolean = true) {
    every { recipients.signalStore.settings.unreadReminderEnabled } returns unreadReminder
    every { recipients.signalStore.settings.allowMentionsWhileMuted } returns mentions
    every { recipients.signalStore.settings.allowRepliesWhileMuted } returns replies
  }

  private fun isMutedFor(threshold: Long = 0): Boolean {
    return threadId in SignalDatabase.threads.getMutedThreadIds(threshold)
  }

  @Test
  fun `allow-by-default plus system-default recipient setting includes the thread`() {
    globalDefaults(unreadReminder = true)
    SignalDatabase.recipients.setUnreadReminder(contactId, RecipientTable.NotificationSetting.SYSTEM_DEFAULT)
    SignalDatabase.threads.incrementUnread(threadId, 1, 1)
    assertThat(isMutedFor()).isTrue()
  }

  @Test
  fun `allow-by-default plus an explicit opt-out excludes the thread`() {
    globalDefaults(unreadReminder = true)
    SignalDatabase.recipients.setUnreadReminder(contactId, RecipientTable.NotificationSetting.DO_NOT_NOTIFY)
    assertThat(isMutedFor()).isFalse()
  }

  @Test
  fun `allow-by-default plus an explicit always-allow includes the thread`() {
    globalDefaults(unreadReminder = true)
    SignalDatabase.recipients.setUnreadReminder(contactId, RecipientTable.NotificationSetting.ALWAYS_NOTIFY)
    SignalDatabase.threads.incrementUnread(threadId, 1, 1)
    assertThat(isMutedFor()).isTrue()
  }

  @Test
  fun `always-allow-required plus system-default recipient setting excludes the thread`() {
    globalDefaults(unreadReminder = false)
    SignalDatabase.recipients.setUnreadReminder(contactId, RecipientTable.NotificationSetting.SYSTEM_DEFAULT)
    assertThat(isMutedFor()).isFalse()
  }

  @Test
  fun `always-allow-required plus an explicit opt-out excludes the thread`() {
    globalDefaults(unreadReminder = true)
    SignalDatabase.recipients.setUnreadReminder(contactId, RecipientTable.NotificationSetting.DO_NOT_NOTIFY)
    assertThat(isMutedFor()).isFalse()
  }

  @Test
  fun `always-allow-required plus an explicit always-allow includes the thread`() {
    globalDefaults(unreadReminder = false)
    SignalDatabase.recipients.setUnreadReminder(contactId, RecipientTable.NotificationSetting.ALWAYS_NOTIFY)
    SignalDatabase.threads.incrementUnread(threadId, 1, 1)
    assertThat(isMutedFor()).isTrue()
  }

  @Test
  fun `an unmuted thread is excluded even when always-allowed`() {
    globalDefaults(unreadReminder = true)
    SignalDatabase.recipients.setUnreadReminder(contactId, RecipientTable.NotificationSetting.ALWAYS_NOTIFY)
    SignalDatabase.recipients.setMuted(contactId, 0L)
    assertThat(isMutedFor()).isFalse()
  }

  @Test
  fun `a thread reminded within the last three days is excluded`() {
    globalDefaults(unreadReminder = true)
    SignalDatabase.recipients.setUnreadReminder(contactId, RecipientTable.NotificationSetting.ALWAYS_NOTIFY)
    SignalDatabase.threads.setUnreadReminderTime(threadId, System.currentTimeMillis())
    assertThat(isMutedFor(3.days.inWholeMilliseconds)).isFalse()
  }

  @Test
  fun `a thread reminded more than three days ago is included again`() {
    globalDefaults(unreadReminder = true)
    SignalDatabase.recipients.setUnreadReminder(contactId, RecipientTable.NotificationSetting.ALWAYS_NOTIFY)
    SignalDatabase.threads.setUnreadReminderTime(threadId, System.currentTimeMillis() - 4.days.inWholeMilliseconds)
    SignalDatabase.threads.incrementUnread(threadId, 1, 1)
    assertThat(isMutedFor(3.days.inWholeMilliseconds)).isTrue()
  }

  @Test
  fun `a thread that does not have any unread is not included`() {
    globalDefaults(unreadReminder = true)
    SignalDatabase.recipients.setUnreadReminder(contactId, RecipientTable.NotificationSetting.ALWAYS_NOTIFY)
    assertThat(isMutedFor()).isFalse()
  }

  @Test
  fun `an archived thread is excluded even when muted and always-allowed`() {
    globalDefaults(unreadReminder = true)
    SignalDatabase.recipients.setUnreadReminder(contactId, RecipientTable.NotificationSetting.ALWAYS_NOTIFY)
    SignalDatabase.threads.setArchived(setOf(threadId), true)
    assertThat(isMutedFor()).isFalse()
  }
}
