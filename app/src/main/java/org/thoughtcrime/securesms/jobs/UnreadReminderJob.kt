package org.thoughtcrime.securesms.jobs

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.content.LocusIdCompat
import org.signal.core.util.PendingIntentFlags
import org.signal.core.util.ServiceUtil
import org.signal.core.util.Stopwatch
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.fallback.FallbackAvatar
import org.thoughtcrime.securesms.avatar.fallback.FallbackAvatarDrawable
import org.thoughtcrime.securesms.components.settings.app.notifications.ReminderType
import org.thoughtcrime.securesms.conversation.ConversationIntents
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobs.protos.UnreadReminderJobData
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.notifications.NotificationIds
import org.thoughtcrime.securesms.notifications.v2.getContactDrawable
import org.thoughtcrime.securesms.notifications.v2.makeUniqueToPreventMerging
import org.thoughtcrime.securesms.notifications.v2.toLargeBitmap
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.AvatarUtil
import org.thoughtcrime.securesms.util.ConversationUtil

/**
 * Job that sends an unread reminder notification for a single muted thread.
 */
class UnreadReminderJob(private val threadId: Long, private val lastReminderTime: Long, parameters: Parameters) : Job(parameters) {

  companion object {
    private val TAG = Log.tag(UnreadReminderJob::class.java)
    const val KEY = "UnreadReminderJob"

    @JvmStatic
    fun enqueue(threadId: Long, lastReminderTime: Long) {
      if (!NotificationChannels.getInstance().areNotificationsEnabled()) {
        return
      }

      AppDependencies.jobManager.add(
        UnreadReminderJob(
          threadId = threadId,
          lastReminderTime = lastReminderTime,
          parameters = Parameters.Builder()
            .setGlobalPriority(Parameters.PRIORITY_LOWER)
            .setQueue("UnreadReminderJob_$threadId")
            .setMaxInstancesForQueue(1)
            .build()
        )
      )
    }

    @VisibleForTesting
    fun create(): UnreadReminderJob {
      return UnreadReminderJob(
        threadId = 1,
        lastReminderTime = 0,
        parameters = Parameters.Builder()
          .setGlobalPriority(Parameters.PRIORITY_LOWER)
          .setMaxInstancesForFactory(1)
          .build()
      )
    }
  }

  override fun serialize(): ByteArray = UnreadReminderJobData(threadId = threadId, lastReminderTime = lastReminderTime).encode()

  override fun getFactoryKey(): String = KEY

  override fun run(): Result {
    val stopwatch = Stopwatch("unread-reminder")
    if (!SignalStore.account.isRegistered) {
      Log.w(TAG, "Not registered. Skipping.")
      return Result.success()
    }

    val recipient = SignalDatabase.threads.getRecipientForThreadId(threadId)
    if (recipient == null) {
      Log.w(TAG, "Missing recipient for thread $threadId.")
      return Result.success()
    }

    if (recipient.unreadReminderSetting == RecipientTable.NotificationSetting.DO_NOT_NOTIFY || !recipient.isMuted) {
      Log.w(TAG, "Recipient ${recipient.id} no longer qualifies for unread reminders")
      return Result.success()
    }

    val hasNewMessages = SignalDatabase.messages.hasUnreadMessagesSince(threadId, lastReminderTime)
    if (!hasNewMessages) {
      Log.i(TAG, "No new unread messages for thread $threadId since last reminder. Skipping.")
      return Result.success()
    }

    val hideAuthors = SignalStore.settings.messageNotificationsPrivacy.isDisplayNothing

    // Get the unread counts/authors
    val (messages, unreadAuthorIds) = getUnreadForReminder(ReminderType.MESSAGES, isEligible = true)
    stopwatch.split("fetch-messages")
    val (mentions, mentionsAuthorIds) = getUnreadForReminder(ReminderType.MENTIONS, isEligible = !hideAuthors && recipient.isPushV2Group && recipient.mentionSetting == RecipientTable.NotificationSetting.ALWAYS_NOTIFY)
    stopwatch.split("fetch-mentions")
    val (replies, repliesAuthorIds) = getUnreadForReminder(ReminderType.REPLIES, isEligible = !hideAuthors && recipient.isPushV2Group && recipient.replyNotificationSetting == RecipientTable.NotificationSetting.ALWAYS_NOTIFY)
    stopwatch.split("fetch-replies")

    val summary = buildSummary(
      context = context,
      messages = messages,
      mentions = mentions,
      replies = replies,
      messageAuthors = unreadAuthorIds.map { Recipient.resolved(it).getShortDisplayName(context) },
      mentionAuthors = mentionsAuthorIds.map { Recipient.resolved(it).getShortDisplayName(context) },
      replyAuthors = repliesAuthorIds.map { Recipient.resolved(it).getShortDisplayName(context) },
      hideAuthors = hideAuthors
    )

    val notificationId = NotificationIds.getNotificationIdForUnreadReminder(threadId)

    if (summary.isEmpty()) {
      ServiceUtil.getNotificationManager(context).cancel(notificationId)
    } else if (NotificationChannels.getInstance().areNotificationsEnabled()) {
      val contentIntent = ConversationIntents.createBuilderSync(context, recipient.id, threadId).build().makeUniqueToPreventMerging()
      val avatar = if (!hideAuthors) recipient.getContactDrawable(context) else FallbackAvatarDrawable(context, FallbackAvatar.forTextOrDefault("Unknown", AvatarColor.UNKNOWN)).circleCrop()

      val person = Person.Builder()
        .setName(recipient.getDisplayName(context))
        .setIcon(AvatarUtil.getIconCompat(context, recipient))
        .build()
      val messagingStyle: NotificationCompat.MessagingStyle = NotificationCompat.MessagingStyle(Person.Builder().setName(context.getString(R.string.SingleRecipientNotificationBuilder_you)).build())
      messagingStyle.addMessage(NotificationCompat.MessagingStyle.Message(summary, System.currentTimeMillis(), person))

      val builder = NotificationCompat.Builder(context, NotificationChannels.getInstance().UNREAD_REMINDERS)
        .setSmallIcon(R.drawable.ic_notification)
        .setLargeIcon(avatar.toLargeBitmap(context))
        .setContentText(summary)
        .setStyle(messagingStyle.takeIf { !hideAuthors })
        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
        .setShortcutId(ConversationUtil.getShortcutId(recipient))
        .setLocusId(LocusIdCompat(ConversationUtil.getShortcutId(recipient)))
        .setContentIntent(PendingIntent.getActivity(context, 0, contentIntent, PendingIntentFlags.updateCurrent()))
        .setAutoCancel(true)

      ContextCompat.getSystemService(context, NotificationManager::class.java)!!.notify(notificationId, builder.build())
    }

    SignalDatabase.threads.setUnreadReminderTime(threadId, System.currentTimeMillis())
    stopwatch.stop(TAG)
    return Result.success()
  }

  /**
   * Builds the full notification summary including types of messages as well as who it is from, see [buildAuthorSummary]
   */
  @VisibleForTesting
  internal fun buildSummary(
    context: Context,
    messages: Int = 0,
    mentions: Int = 0,
    replies: Int = 0,
    messageAuthors: List<String> = emptyList(),
    mentionAuthors: List<String> = emptyList(),
    replyAuthors: List<String> = emptyList(),
    hideAuthors: Boolean = false
  ): String {
    val showUnread = messages > 0
    val showAuthors = showUnread && !hideAuthors
    val showMentions = mentions > 0 && !hideAuthors
    val showReplies = replies > 0 && !hideAuthors

    val messagesString = if (showUnread) context.resources.getQuantityString(R.plurals.UnreadReminderJob__messages, messages, messages) else ""
    val authorsString = if (showAuthors) buildAuthorSummary(context, ReminderType.MESSAGES, messages, messageAuthors) else ""
    val mentionsString = if (showMentions) buildAuthorSummary(context, ReminderType.MENTIONS, mentions, mentionAuthors) else ""
    val repliesString = if (showReplies) buildAuthorSummary(context, ReminderType.REPLIES, replies, replyAuthors) else ""

    return if (showUnread && showMentions && showReplies) {
      context.getString(R.string.UnreadReminderJob__unread_both_summary, messagesString, mentionsString, repliesString)
    } else if (showUnread && showMentions) {
      context.getString(R.string.UnreadReminderJob__unread_one_summary, messagesString, mentionsString)
    } else if (showUnread && showReplies) {
      context.getString(R.string.UnreadReminderJob__unread_one_summary, messagesString, repliesString)
    } else if (hideAuthors && showUnread) {
      context.getString(R.string.UnreadReminderJob__calls_or_unread, messagesString)
    } else if (showUnread) {
      context.getString(R.string.UnreadReminderJob__calls_or_unread_author, messagesString, authorsString)
    } else {
      ""
    }
  }

  /**
   * Formats the authors and number of messages based off of reminder type and number of authors
   * eg '5 mentions from Alice and others' or 'a reply from Bob'
   */
  @VisibleForTesting
  internal fun buildAuthorSummary(context: Context, reminderType: ReminderType, count: Int, authors: List<String>): String {
    val (oneRes, twoRes, manyRes) = when (reminderType) {
      ReminderType.MESSAGES -> Triple(R.string.UnreadReminderJob__authors_one, R.string.UnreadReminderJob__authors_two, R.string.UnreadReminderJob__authors_many)
      ReminderType.MENTIONS -> Triple(R.string.UnreadReminderJob__mentions_one, R.plurals.UnreadReminderJob__mentions_two, R.plurals.UnreadReminderJob__mentions_many)
      ReminderType.REPLIES -> Triple(R.string.UnreadReminderJob__replies_one, R.plurals.UnreadReminderJob__replies_two, R.plurals.UnreadReminderJob__replies_many)
    }
    val includesCount = reminderType == ReminderType.MENTIONS || reminderType == ReminderType.REPLIES

    return when (authors.size) {
      1 -> context.getString(oneRes, authors[0])
      2 -> if (includesCount) {
        context.resources.getQuantityString(twoRes, count, count, authors[0], authors[1])
      } else {
        context.getString(twoRes, authors[0], authors[1])
      }
      else -> if (includesCount) {
        context.resources.getQuantityString(manyRes, count, count, authors[0])
      } else {
        context.getString(manyRes, authors[0])
      }
    }
  }

  private fun getUnreadForReminder(reminderType: ReminderType, isEligible: Boolean): Pair<Int, List<RecipientId>> {
    return if (!isEligible) {
      0 to emptyList()
    } else {
      SignalDatabase.messages.getUnreadContentForReminderNotification(threadId, reminderType)
    }
  }

  override fun onFailure() {
    Log.w(TAG, "Failed to create unread reminder notification for thread $threadId")
  }

  class Factory : Job.Factory<UnreadReminderJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): UnreadReminderJob {
      val data = UnreadReminderJobData.ADAPTER.decode(serializedData!!)
      return UnreadReminderJob(threadId = data.threadId, lastReminderTime = data.lastReminderTime, parameters = parameters)
    }
  }
}
