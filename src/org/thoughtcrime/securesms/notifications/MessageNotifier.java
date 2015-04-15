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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.Action;
import android.support.v4.app.NotificationCompat.BigTextStyle;
import android.support.v4.app.NotificationCompat.InboxStyle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.util.Log;

import org.thoughtcrime.securesms.ConversationActivity;
import org.thoughtcrime.securesms.ConversationListActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.PushDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.SpanUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.textsecure.api.messages.TextSecureEnvelope;

import java.io.IOException;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.TimeUnit;

import me.leolin.shortcutbadger.ShortcutBadger;


/**
 * Handles posting system notifications for new messages.
 *
 *
 * @author Moxie Marlinspike
 */

public class MessageNotifier {

  public static final int NOTIFICATION_ID = 1338;

  private volatile static long visibleThread = -1;

  public static void setVisibleThread(long threadId) {
    visibleThread = threadId;
  }

  public static void notifyMessageDeliveryFailed(Context context, Recipients recipients, long threadId) {
    if (visibleThread == threadId) {
      sendInThreadNotification(context);
    } else {
      Intent intent = new Intent(context, ConversationActivity.class);
      intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
      intent.putExtra("recipients", recipients.getIds());
      intent.putExtra("thread_id", threadId);
      intent.setData((Uri.parse("custom://"+System.currentTimeMillis())));

      NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
      builder.setSmallIcon(R.drawable.icon_notification);
      builder.setLargeIcon(BitmapFactory.decodeResource(context.getResources(),
                                                        R.drawable.ic_action_warning_red));
      builder.setContentTitle(context.getString(R.string.MessageNotifier_message_delivery_failed));
      builder.setContentText(context.getString(R.string.MessageNotifier_failed_to_deliver_message));
      builder.setTicker(context.getString(R.string.MessageNotifier_error_delivering_message));
      builder.setContentIntent(PendingIntent.getActivity(context, 0, intent, 0));
      builder.setAutoCancel(true);
      setNotificationAlarms(context, builder, true);

      ((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE))
        .notify((int)threadId, builder.build());
    }
  }

  public static void updateNotification(Context context, MasterSecret masterSecret) {
    if (!TextSecurePreferences.isNotificationsEnabled(context)) {
      return;
    }

    updateNotification(context, masterSecret, false, 0);
  }

  public static void updateNotification(Context context, MasterSecret masterSecret, long threadId) {
    if (!TextSecurePreferences.isNotificationsEnabled(context)) {
      return;
    }

    if (visibleThread == threadId) {
      DatabaseFactory.getThreadDatabase(context).setRead(threadId);
      sendInThreadNotification(context);
    } else {
      updateNotification(context, masterSecret, true, 0);
    }
  }

  private static void updateNotification(Context context, MasterSecret masterSecret, boolean signal, int reminderCount) {
    Cursor telcoCursor = null;
    Cursor pushCursor  = null;

    try {
      telcoCursor = DatabaseFactory.getMmsSmsDatabase(context).getUnread();
      pushCursor  = DatabaseFactory.getPushDatabase(context).getPending();

      if ((telcoCursor == null || telcoCursor.isAfterLast()) &&
          (pushCursor == null || pushCursor.isAfterLast()))
      {
        ((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE))
          .cancel(NOTIFICATION_ID);
        updateBadge(context, 0);
        clearReminder(context);
        return;
      }

      NotificationState notificationState = constructNotificationState(context, masterSecret, telcoCursor);

      appendPushNotificationState(context, masterSecret, notificationState, pushCursor);

      if (notificationState.hasMultipleThreads()) {
        sendMultipleThreadNotification(context, masterSecret, notificationState, signal);
      } else {
        sendSingleThreadNotification(context, masterSecret, notificationState, signal);
      }

      updateBadge(context, notificationState.getMessageCount());
      scheduleReminder(context, masterSecret, reminderCount);
    } finally {
      if (telcoCursor != null) telcoCursor.close();
      if (pushCursor != null)  pushCursor.close();
    }
  }

  private static void sendSingleThreadNotification(Context context,
                                                   MasterSecret masterSecret,
                                                   NotificationState notificationState,
                                                   boolean signal)
  {
    if (notificationState.getNotifications().isEmpty()) {
      ((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE))
          .cancel(NOTIFICATION_ID);
      return;
    }

    List<NotificationItem>     notifications  = notificationState.getNotifications();
    NotificationCompat.Builder builder        = new NotificationCompat.Builder(context);
    Recipient                  recipient      = notifications.get(0).getIndividualRecipient();
    Bitmap                     recipientPhoto = recipient.getContactPhoto();

    if (recipientPhoto != null) builder.setLargeIcon(BitmapUtil.getCircleBitmap(recipientPhoto));
    builder.setSmallIcon(R.drawable.icon_notification);
    builder.setColor(context.getResources().getColor(R.color.textsecure_primary));
    builder.setContentTitle(recipient.toShortString());
    builder.setContentText(notifications.get(0).getText());
    builder.setContentIntent(notifications.get(0).getPendingIntent(context));
    builder.setContentInfo(String.valueOf(notificationState.getMessageCount()));
    builder.setPriority(NotificationCompat.PRIORITY_HIGH);
    builder.setNumber(notificationState.getMessageCount());
    builder.setCategory(NotificationCompat.CATEGORY_MESSAGE);
    builder.setDeleteIntent(PendingIntent.getBroadcast(context, 0, new Intent(DeleteReceiver.DELETE_REMINDER_ACTION), 0));
    if (recipient.getContactUri() != null) builder.addPerson(recipient.getContactUri().toString());

    long timestamp = notifications.get(0).getTimestamp();
    if (timestamp != 0) builder.setWhen(timestamp);

    if (masterSecret != null) {
      Action markAsReadAction = new Action(R.drawable.check,
                                           context.getString(R.string.MessageNotifier_mark_as_read),
                                           notificationState.getMarkAsReadIntent(context, masterSecret));
      builder.addAction(markAsReadAction);
      builder.extend(new NotificationCompat.WearableExtender().addAction(markAsReadAction));
    }

    SpannableStringBuilder content = new SpannableStringBuilder();

    ListIterator<NotificationItem> iterator = notifications.listIterator(notifications.size());
    while(iterator.hasPrevious()) {
      NotificationItem item = iterator.previous();
      content.append(item.getBigStyleSummary());
      content.append('\n');
    }

    builder.setStyle(new BigTextStyle().bigText(content));

    setNotificationAlarms(context, builder, signal);

    if (signal) {
      builder.setTicker(notifications.get(0).getTickerText());
    }

    ((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE))
      .notify(NOTIFICATION_ID, builder.build());
  }

  private static void sendMultipleThreadNotification(Context context,
                                                     MasterSecret masterSecret,
                                                     NotificationState notificationState,
                                                     boolean signal)
  {
    List<NotificationItem> notifications = notificationState.getNotifications();
    NotificationCompat.Builder builder   = new NotificationCompat.Builder(context);

    builder.setColor(context.getResources().getColor(R.color.textsecure_primary));
    builder.setSmallIcon(R.drawable.icon_notification);
    builder.setContentTitle(context.getString(R.string.app_name));
    builder.setSubText(context.getString(R.string.MessageNotifier_d_messages_in_d_conversations,
                                         notificationState.getMessageCount(),
                                         notificationState.getThreadCount()));
    builder.setContentText(context.getString(R.string.MessageNotifier_most_recent_from_s,
                                             notifications.get(0).getIndividualRecipientName()));
    builder.setContentIntent(PendingIntent.getActivity(context, 0, new Intent(context, ConversationListActivity.class), 0));
    
    builder.setContentInfo(String.valueOf(notificationState.getMessageCount()));
    builder.setNumber(notificationState.getMessageCount());
    builder.setCategory(NotificationCompat.CATEGORY_MESSAGE);

    long timestamp = notifications.get(0).getTimestamp();
    if (timestamp != 0) builder.setWhen(timestamp);

    builder.setDeleteIntent(PendingIntent.getBroadcast(context, 0, new Intent(DeleteReceiver.DELETE_REMINDER_ACTION), 0));

    if (masterSecret != null) {
       Action markAllAsReadAction = new Action(R.drawable.check,
                                               context.getString(R.string.MessageNotifier_mark_all_as_read),
                                               notificationState.getMarkAsReadIntent(context, masterSecret));
       builder.addAction(markAllAsReadAction);
       builder.extend(new NotificationCompat.WearableExtender().addAction(markAllAsReadAction));
    }

    InboxStyle style = new InboxStyle();

    ListIterator<NotificationItem> iterator = notifications.listIterator(notifications.size());
    while(iterator.hasPrevious()) {
      NotificationItem item = iterator.previous();
      style.addLine(item.getTickerText());
      if (item.getIndividualRecipient().getContactUri() != null) {
        builder.addPerson(item.getIndividualRecipient().getContactUri().toString());
      }
    }

    builder.setStyle(style);

    setNotificationAlarms(context, builder, signal);

    if (signal) {
      builder.setTicker(notifications.get(0).getTickerText());
    }

    ((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE))
      .notify(NOTIFICATION_ID, builder.build());
  }

  private static void sendInThreadNotification(Context context) {
    try {
      if (!TextSecurePreferences.isInThreadNotifications(context)) {
        return;
      }

      String ringtone = TextSecurePreferences.getNotificationRingtone(context);

      if (ringtone == null)
        return;

      Uri uri            = Uri.parse(ringtone);
      MediaPlayer player = new MediaPlayer();
      player.setAudioStreamType(AudioManager.STREAM_NOTIFICATION);
      player.setDataSource(context, uri);
      player.setLooping(false);
      player.setVolume(0.25f, 0.25f);
      player.prepare();

      final AudioManager audioManager = ((AudioManager)context.getSystemService(Context.AUDIO_SERVICE));

      audioManager.requestAudioFocus(null, AudioManager.STREAM_NOTIFICATION,
                                     AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);

      player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
        @Override
        public void onCompletion(MediaPlayer mp) {
          audioManager.abandonAudioFocus(null);
        }
      });

      player.start();
    } catch (IOException ioe) {
      Log.w("MessageNotifier", ioe);
    }
  }

  private static void appendPushNotificationState(Context context,
                                                  MasterSecret masterSecret,
                                                  NotificationState notificationState,
                                                  Cursor cursor)
  {
    if (masterSecret != null) return;

    PushDatabase.Reader reader = null;
    TextSecureEnvelope envelope;

    try {
      reader = DatabaseFactory.getPushDatabase(context).readerFor(cursor);

      while ((envelope = reader.getNext()) != null) {
        Recipients      recipients = RecipientFactory.getRecipientsFromString(context, envelope.getSource(), false);
        Recipient       recipient  = recipients.getPrimaryRecipient();
        long            threadId   = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipients);
        SpannableString body       = new SpannableString(context.getString(R.string.MessageNotifier_encrypted_message));
        body.setSpan(new StyleSpan(android.graphics.Typeface.ITALIC), 0, body.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        notificationState.addNotification(new NotificationItem(recipient, recipients, null, threadId, body, null, 0));
      }
    } finally {
      if (reader != null)
        reader.close();
    }
  }

  private static NotificationState constructNotificationState(Context context,
                                                              MasterSecret masterSecret,
                                                              Cursor cursor)
  {
    NotificationState notificationState = new NotificationState();
    MessageRecord record;
    MmsSmsDatabase.Reader reader;

    if (masterSecret == null) reader = DatabaseFactory.getMmsSmsDatabase(context).readerFor(cursor);
    else                      reader = DatabaseFactory.getMmsSmsDatabase(context).readerFor(cursor, masterSecret);

    while ((record = reader.getNext()) != null) {
      Recipient       recipient        = record.getIndividualRecipient();
      Recipients      recipients       = record.getRecipients();
      long            threadId         = record.getThreadId();
      CharSequence    body             = record.getDisplayBody();
      Uri             image            = null;
      Recipients      threadRecipients = null;
      long            timestamp;

      if (record.isPush()) timestamp = record.getDateSent();
      else                 timestamp = record.getDateReceived();

      if (threadId != -1) {
        threadRecipients = DatabaseFactory.getThreadDatabase(context).getRecipientsForThreadId(threadId);
      }

      if (SmsDatabase.Types.isDecryptInProgressType(record.getType()) || !record.getBody().isPlaintext()) {
        body = SpanUtil.italic(context.getString(R.string.MessageNotifier_encrypted_message));
      } else if (record.isMms() && TextUtils.isEmpty(body)) {
        body = SpanUtil.italic(context.getString(R.string.MessageNotifier_media_message));
      } else if (record.isMms()) {
        String message      = context.getString(R.string.MessageNotifier_media_message_with_text, body);
        int    italicLength = message.length() - body.length();
        body = SpanUtil.italic(message, italicLength);
      }

      notificationState.addNotification(new NotificationItem(recipient, recipients, threadRecipients, threadId, body, image, timestamp));
    }

    reader.close();
    return notificationState;
  }

  private static void setNotificationAlarms(Context context,
                                            NotificationCompat.Builder builder,
                                            boolean signal)
  {
    String ringtone              = TextSecurePreferences.getNotificationRingtone(context);
    boolean vibrate              = TextSecurePreferences.isNotificationVibrateEnabled(context);
    String ledColor              = TextSecurePreferences.getNotificationLedColor(context);
    String ledBlinkPattern       = TextSecurePreferences.getNotificationLedPattern(context);
    String ledBlinkPatternCustom = TextSecurePreferences.getNotificationLedPatternCustom(context);
    String[] blinkPatternArray   = parseBlinkPattern(ledBlinkPattern, ledBlinkPatternCustom);

    builder.setSound(TextUtils.isEmpty(ringtone) || !signal ? null : Uri.parse(ringtone));

    if (signal && vibrate) {
      builder.setDefaults(Notification.DEFAULT_VIBRATE);
    }

    if (!ledColor.equals("none")) {
      builder.setLights(Color.parseColor(ledColor),
                        Integer.parseInt(blinkPatternArray[0]),
                        Integer.parseInt(blinkPatternArray[1]));
    }
  }

  private static String[] parseBlinkPattern(String blinkPattern, String blinkPatternCustom) {
    if (blinkPattern.equals("custom"))
      blinkPattern = blinkPatternCustom;

    return blinkPattern.split(",");
  }

  private static void updateBadge(Context context, int count) {
    try {
      ShortcutBadger.setBadge(context, count);
    } catch (Throwable t) {
      // NOTE :: I don't totally trust this thing, so I'm catching
      // everything.
      Log.w("MessageNotifier", t);
    }
  }

  private static void scheduleReminder(Context context, MasterSecret masterSecret, int count) {
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

  public static class ReminderReceiver extends BroadcastReceiver {

    public static final String REMINDER_ACTION = "org.thoughtcrime.securesms.MessageNotifier.REMINDER_ACTION";

    @Override
    public void onReceive(Context context, Intent intent) {
      MasterSecret masterSecret  = KeyCachingService.getMasterSecret(context);
      int          reminderCount = intent.getIntExtra("reminder_count", 0);
      MessageNotifier.updateNotification(context, masterSecret, true, reminderCount + 1);
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
