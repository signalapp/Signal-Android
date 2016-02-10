/**
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.notifications;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.util.Log;

import org.thoughtcrime.securesms.ConversationActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.PushDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.util.SpanUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libaxolotl.util.guava.Function;
import org.whispersystems.libaxolotl.util.guava.Optional;
import org.whispersystems.textsecure.api.messages.TextSecureEnvelope;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import me.leolin.shortcutbadger.ShortcutBadger;


/**
 * Handles posting system notifications for new messages.
 *
 *
 * @author Moxie Marlinspike
 */

public class MessageNotifier {

  private static final String TAG = MessageNotifier.class.getSimpleName();

  public static final int NOTIFICATION_ID = 1338;

  private volatile static long visibleThread = -1;

  public static final String EXTRA_VOICE_REPLY = "extra_voice_reply";

  public static void setVisibleThread(long threadId) {
    visibleThread = threadId;
  }

  public static void notifyMessageDeliveryFailed(Context context, Recipients recipients, long threadId) {
    if (visibleThread == threadId) {
      sendInThreadNotification(context, recipients);
    } else {
      Intent intent = new Intent(context, ConversationActivity.class);
      intent.putExtra(ConversationActivity.RECIPIENTS_EXTRA, recipients.getIds());
      intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, threadId);
      intent.setData((Uri.parse("custom://" + System.currentTimeMillis())));

      FailedNotificationBuilder builder = new FailedNotificationBuilder(context, TextSecurePreferences.getNotificationPrivacy(context), intent);
      NotificationManagerCompat.from(context).notify((int)threadId, builder.build());
    }
  }

  /**
   * See {@link #updateNotificationCancelRead(Context, MasterSecret, Set)}.
   *
   * @param context Current context
   * @param masterSecret Master secret
   * @param threadId Thread id to cancel notification for
   */
  public static void updateNotificationCancelRead(@NonNull Context context, @Nullable MasterSecret masterSecret, @NonNull Long threadId) {
    updateNotificationCancelRead(context, masterSecret, new HashSet<Long>(Arrays.asList(threadId)));
  }

  /**
   * Cancels the notifications for the given threadIds and calls {@link #updateNotification(Context, MasterSecret)}.
   *
   * @param context Current context
   * @param masterSecret Master secret
   * @param threadIds Thread ids to cancel notifications for
   */
  public static void updateNotificationCancelRead(@NonNull Context context, @Nullable MasterSecret masterSecret, @NonNull Set<Long> threadIds) {
    for(Long threadId : threadIds) {
      NotificationManagerCompat.from(context).cancel(threadId + "", NOTIFICATION_ID);
    }

    updateNotification(context, masterSecret);
  }

  public static void updateNotification(@NonNull Context context, @Nullable MasterSecret masterSecret) {
    if (!TextSecurePreferences.isNotificationsEnabled(context)) {
      return;
    }

    updateNotification(context, masterSecret, false, false, 0);
  }

  public static void updateNotification(@NonNull  Context context,
                                        @Nullable MasterSecret masterSecret,
                                        long threadId)
  {
    updateNotification(context, masterSecret, false, threadId);
  }

  public static void updateNotification(@NonNull  Context context,
                                        @Nullable MasterSecret masterSecret,
                                        boolean   includePushDatabase,
                                        long      threadId)
  {
    updateNotification(context, masterSecret, includePushDatabase, threadId, true);
  }

  public static void updateNotification(@NonNull  Context context,
                                        @Nullable MasterSecret masterSecret,
                                        boolean   includePushDatabase,
                                        long      threadId,
                                        boolean   signal)
  {
    boolean    isVisible  = visibleThread == threadId;

    ThreadDatabase threads    = DatabaseFactory.getThreadDatabase(context);
    Recipients     recipients = DatabaseFactory.getThreadDatabase(context)
                                               .getRecipientsForThreadId(threadId);

    if (isVisible) {
      threads.setRead(threadId);
    }

    if (!TextSecurePreferences.isNotificationsEnabled(context) ||
        (recipients != null && recipients.isMuted()))
    {
      return;
    }

    if (isVisible) {
      sendInThreadNotification(context, threads.getRecipientsForThreadId(threadId));
    } else {
      updateNotification(context, masterSecret, signal, includePushDatabase, 0);
    }
  }

  public static Set<Long> unreadThreadIds(@NonNull  Context context,
                                           @Nullable MasterSecret masterSecret) {
    return currentNotificationState(context, masterSecret, false).transform(new Function<NotificationState, Set<Long>>() {
      @Override
      public Set<Long> apply(NotificationState input) {
        return input.getThreads();
      }
    }).or(new HashSet<Long>());
  }

  private static Optional<NotificationState> currentNotificationState(@NonNull  Context context,
                                                           @Nullable MasterSecret masterSecret,
                                                           boolean includePushDatabase) {
    Cursor telcoCursor = null;
    Cursor pushCursor  = null;

    try {
      telcoCursor = DatabaseFactory.getMmsSmsDatabase(context).getUnread();
      pushCursor  = DatabaseFactory.getPushDatabase(context).getPending();

      if ((telcoCursor == null || telcoCursor.isAfterLast()) &&
              (pushCursor == null || pushCursor.isAfterLast())) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID);
        updateBadge(context, 0);
        clearReminder(context);
        return Optional.absent();
      }

      NotificationState notificationState = constructNotificationState(context, masterSecret, telcoCursor);

      if (includePushDatabase) {
        appendPushNotificationState(context, notificationState, pushCursor);
      }

      return Optional.of(notificationState);
    } finally {
      if (telcoCursor != null) telcoCursor.close();
      if (pushCursor != null)  pushCursor.close();
    }
  }

  private static void updateNotification(@NonNull  Context context,
                                         @Nullable MasterSecret masterSecret,
                                         boolean signal,
                                         boolean includePushDatabase,
                                         int     reminderCount)
  {
    Optional<NotificationState> notificationStateOption = currentNotificationState(context, masterSecret, includePushDatabase);

    if(!notificationStateOption.isPresent())
      return;

    boolean bug130689NotificationFixEnabled = TextSecurePreferences.isBug130689NotificationFixEnabled(context);
    boolean hasWearSupport = hasWearSupport();
    boolean hasNotificationSummaryAlarmBug = hasNotificationSummaryAlarmBug();
    boolean displayWearableNotifications = hasWearSupport && (!hasNotificationSummaryAlarmBug || bug130689NotificationFixEnabled);
    boolean displayBugFixNotification = hasWearSupport && hasNotificationSummaryAlarmBug && bug130689NotificationFixEnabled;

    NotificationState notificationState = notificationStateOption.get();

    if (notificationState.hasMultipleThreads()) {
        sendMultipleThreadNotification(context, masterSecret, notificationState, signal, displayBugFixNotification, displayWearableNotifications);
      } else {
        sendSingleThreadNotification(context, masterSecret, notificationState, signal, displayWearableNotifications);
      }

      updateBadge(context, notificationState.getMessageCount());

      if (signal) {
        scheduleReminder(context, reminderCount);
      }

  }

  private static SingleRecipientNotificationBuilder singleThreadNotificationBuilder(@NonNull  Context context,
                                                   @Nullable MasterSecret masterSecret,
                                                   @NonNull  NotificationState notificationState,
                                                   boolean signal) {
    SingleRecipientNotificationBuilder builder       = new SingleRecipientNotificationBuilder(context, masterSecret, TextSecurePreferences.getNotificationPrivacy(context));
    List<NotificationItem>             notifications = notificationState.getNotifications();
    Recipients                         recipients    = notifications.get(0).getRecipients();

    builder.setThread(notifications.get(0).getRecipients());
    builder.setMessageCount(notificationState.getMessageCount());
    builder.setPrimaryMessageBody(recipients, notifications.get(0).getIndividualRecipient(),
                                  notifications.get(0).getText(), notifications.get(0).getSlideDeck());
    builder.setContentIntent(notifications.get(0).getPendingIntent(context));

    long timestamp = notifications.get(0).getTimestamp();
    if (timestamp != 0) builder.setWhen(timestamp);

    builder.addActions(masterSecret,
                       notificationState.getMarkAsReadIntent(context, (int) notifications.get(0).getThreadId()),
                       notificationState.getQuickReplyIntent(context, notifications.get(0).getRecipients()),
                       notificationState.getWearableReplyIntent(context, notifications.get(0).getRecipients()));

    ListIterator<NotificationItem> iterator = notifications.listIterator(notifications.size());

    while(iterator.hasPrevious()) {
      NotificationItem item = iterator.previous();
      builder.addMessageBody(item.getRecipients(), item.getIndividualRecipient(), item.getText());
    }

    if (signal) {
      builder.setAlarms(notificationState.getRingtone(), notificationState.getVibrate());
      builder.setTicker(notifications.get(0).getIndividualRecipient(),
                        notifications.get(0).getText());
    }

    return builder;
  }

  public static boolean hasWearSupport() {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2;
  }

  private static void sendSingleThreadNotification(@NonNull Context context,
                                                   @Nullable MasterSecret masterSecret,
                                                   @NonNull NotificationState notificationState,
                                                   boolean signal,
                                                   boolean displayWearableNotifications)
  {
    if (notificationState.getNotifications().isEmpty()) {
      NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID);

      return;
    }

    Long threadId = notificationState.getNotifications().get(0).getThreadId();

    SingleRecipientNotificationBuilder wearNotificationBuilder = singleThreadNotificationBuilder(context, masterSecret, notificationState, signal);

    if(hasWearSupport()) {
      NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID);

      if(displayWearableNotifications) {
        NotificationManagerCompat.from(context).notify(threadId + "", NOTIFICATION_ID, wearNotificationBuilder.build());
      } else {
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, wearNotificationBuilder.build());
      }


      if(doesNotDisplayNonSummaryGroupNotifications()) {
        SingleRecipientNotificationBuilder builder = singleThreadNotificationBuilder(context, masterSecret, notificationState, true);
        builder.setGroupSummary(true);
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build());
      }
    } else {
      NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, wearNotificationBuilder.build());
    }

  }

  private static void sendMultipleThreadNotification(@NonNull  Context context,
                                                     @Nullable MasterSecret masterSecret,
                                                     @NonNull  NotificationState notificationState,
                                                     boolean signal,
                                                     boolean displayBugfixNotification,
                                                     boolean displayWearableNotifications)
  {
    MultipleRecipientNotificationBuilder builder       = new MultipleRecipientNotificationBuilder(context, TextSecurePreferences.getNotificationPrivacy(context));
    List<NotificationItem>               notifications = notificationState.getNotifications();

    builder.setMessageCount(notificationState.getMessageCount(), notificationState.getThreadCount());
    builder.setMostRecentSender(notifications.get(0).getIndividualRecipient());

    long timestamp = notifications.get(0).getTimestamp();
    if (timestamp != 0) builder.setWhen(timestamp);

    builder.addActions(notificationState.getMarkAllAsReadIntent(context));

    ListIterator<NotificationItem> iterator = notifications.listIterator(notifications.size());

    while(iterator.hasPrevious()) {
      NotificationItem item = iterator.previous();
      builder.addMessageBody(item.getIndividualRecipient(), item.getText());
    }

    if (signal) {
      builder.setAlarms(notificationState.getRingtone(), notificationState.getVibrate());
      builder.setTicker(notifications.get(0).getText());

      if(displayBugfixNotification) {
        builder.setPriority(NotificationCompat.PRIORITY_DEFAULT);
      }
    }

    if(displayWearableNotifications) {
      OrderedThreadNotifications notificationStates = notificationState.orderedThreadNotifications();

      NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

      for (Long threadId : notificationStates.getOrderedThreads()) {

        NotificationState state = notificationStates.getNotificationState(threadId);
        SingleRecipientNotificationBuilder singleThreadNotificationBuilder = singleThreadNotificationBuilder(context, masterSecret, state, false);

        notificationManager.notify(threadId + "", NOTIFICATION_ID, singleThreadNotificationBuilder.build());
      }
    } else {
      builder.setGroup("");
      builder.setGroupSummary(false);
    }

    NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build());

  }

  private static void sendInThreadNotification(Context context, Recipients recipients) {
    if (!TextSecurePreferences.isInThreadNotifications(context)) {
      return;
    }

    Uri uri = recipients != null ? recipients.getRingtone() : null;

    if (uri == null) {
      String ringtone = TextSecurePreferences.getNotificationRingtone(context);

      if (ringtone == null) {
        Log.w(TAG, "ringtone preference was null.");
        return;
      }

      uri = Uri.parse(ringtone);

      if (uri == null) {
        Log.w(TAG, "couldn't parse ringtone uri " + ringtone);
        return;
      }
    }

    if (uri.toString().isEmpty()) {
      Log.d(TAG, "ringtone uri is empty");
      return;
    }

    Ringtone ringtone = RingtoneManager.getRingtone(context, uri);

    if (ringtone == null) {
      Log.w(TAG, "ringtone is null");
      return;
    }

    if (Build.VERSION.SDK_INT >= 21) {
      ringtone.setAudioAttributes(new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)
                                                               .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                                                               .build());
    } else {
      ringtone.setStreamType(AudioManager.STREAM_NOTIFICATION);
    }

    ringtone.play();
  }

  private static void appendPushNotificationState(@NonNull Context context,
                                                  @NonNull NotificationState notificationState,
                                                  @NonNull Cursor cursor)
  {
    PushDatabase.Reader reader = null;
    TextSecureEnvelope envelope;

    try {
      reader = DatabaseFactory.getPushDatabase(context).readerFor(cursor);

      while ((envelope = reader.getNext()) != null) {
        Recipients      recipients = RecipientFactory.getRecipientsFromString(context, envelope.getSource(), false);
        Recipient       recipient  = recipients.getPrimaryRecipient();
        long            threadId   = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipients);
        SpannableString body       = new SpannableString(context.getString(R.string.MessageNotifier_locked_message));
        body.setSpan(new StyleSpan(android.graphics.Typeface.ITALIC), 0, body.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        if (!recipients.isMuted()) {
          notificationState.addNotification(new NotificationItem(recipient, recipients, null, threadId, body, 0, null));
        }
      }
    } finally {
      if (reader != null)
        reader.close();
    }
  }

  private static NotificationState constructNotificationState(@NonNull  Context context,
                                                              @Nullable MasterSecret masterSecret,
                                                              @NonNull  Cursor cursor)
  {
    NotificationState notificationState = new NotificationState();
    MessageRecord record;
    MmsSmsDatabase.Reader reader;

    if (masterSecret == null) reader = DatabaseFactory.getMmsSmsDatabase(context).readerFor(cursor);
    else                      reader = DatabaseFactory.getMmsSmsDatabase(context).readerFor(cursor, masterSecret);

    while ((record = reader.getNext()) != null) {
      Recipient    recipient        = record.getIndividualRecipient();
      Recipients   recipients       = record.getRecipients();
      long         threadId         = record.getThreadId();
      CharSequence body             = record.getDisplayBody();
      Recipients   threadRecipients = null;
      SlideDeck    slideDeck        = null;
      long         timestamp        = record.getTimestamp();
      

      if (threadId != -1) {
        threadRecipients = DatabaseFactory.getThreadDatabase(context).getRecipientsForThreadId(threadId);
      }

      if (SmsDatabase.Types.isDecryptInProgressType(record.getType()) || !record.getBody().isPlaintext()) {
        body = SpanUtil.italic(context.getString(R.string.MessageNotifier_locked_message));
      } else if (record.isMms() && TextUtils.isEmpty(body)) {
        body = SpanUtil.italic(context.getString(R.string.MessageNotifier_media_message));
        slideDeck = ((MediaMmsMessageRecord)record).getSlideDeck();
      } else if (record.isMms() && !record.isMmsNotification()) {
        String message      = context.getString(R.string.MessageNotifier_media_message_with_text, body);
        int    italicLength = message.length() - body.length();
        body = SpanUtil.italic(message, italicLength);
        slideDeck = ((MediaMmsMessageRecord)record).getSlideDeck();
      }

      if (threadRecipients == null || !threadRecipients.isMuted()) {
        notificationState.addNotification(new NotificationItem(recipient, recipients, threadRecipients, threadId, body, timestamp, slideDeck));
      }
    }

    reader.close();
    return notificationState;
  }

  private static void updateBadge(Context context, int count) {
    try {
      ShortcutBadger.setBadge(context.getApplicationContext(), count);
    } catch (Throwable t) {
      // NOTE :: I don't totally trust this thing, so I'm catching
      // everything.
      Log.w("MessageNotifier", t);
    }
  }

  private static void scheduleReminder(Context context, int count) {
    if (count >= TextSecurePreferences.getRepeatAlertsCount(context)) {
      return;
    }

    AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    Intent       alarmIntent  = new Intent(ReminderReceiver.REMINDER_ACTION);
    alarmIntent.putExtra("reminder_count", count);

    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, alarmIntent, PendingIntent.FLAG_CANCEL_CURRENT);
    long          timeout       = TimeUnit.MINUTES.toMillis(2);

    alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + timeout, pendingIntent);
  }

  private static void clearReminder(Context context) {
    Intent        alarmIntent   = new Intent(ReminderReceiver.REMINDER_ACTION);
    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, alarmIntent, PendingIntent.FLAG_CANCEL_CURRENT);
    AlarmManager  alarmManager  = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    alarmManager.cancel(pendingIntent);
  }

  /**
   * On certain Android versions (5.0, 5.1) when adding an alarm (ringtone, vibration) to a summary notification, all other non-summary notifications in this group are displayed as well.
   * <br><br>
   * This bug was reported at https://code.google.com/p/android/issues/detail?id=130689
   *
   * @return <b>true</b> if the device is affected, <b>false</b> otherwise
   */
  private static boolean hasNotificationSummaryAlarmBug() {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1;
  }

  /**
   * On Android prior to 5.0, non-summary notifications in a group are not displayed on the handheld.
   * <br><br>
   * This bug was reported at https://code.google.com/p/android/issues/detail?id=159947
   * @return <b>true</b> if the device is affected, <b>false</b> otherwise
   */
  private static boolean doesNotDisplayNonSummaryGroupNotifications() {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP;
  }

  public static class ReminderReceiver extends BroadcastReceiver {

    public static final String REMINDER_ACTION = "org.thoughtcrime.securesms.MessageNotifier.REMINDER_ACTION";

    @Override
    public void onReceive(final Context context, final Intent intent) {
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          MasterSecret masterSecret  = KeyCachingService.getMasterSecret(context);
          int          reminderCount = intent.getIntExtra("reminder_count", 0);
          MessageNotifier.updateNotification(context, masterSecret, true, true, reminderCount + 1);

          return null;
        }
      }.execute();
    }
  }

  public static class DeleteReceiver extends BroadcastReceiver {

    public static final String DELETE_REMINDER_ACTION = "org.thoughtcrime.securesms.MessageNotifier.DELETE_REMINDER_ACTION";

    @Override
    public void onReceive(Context context, Intent intent) {
      clearReminder(context);
    }
  }
}
