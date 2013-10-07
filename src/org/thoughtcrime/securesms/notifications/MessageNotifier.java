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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.BigTextStyle;
import android.support.v4.app.NotificationCompat.InboxStyle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.util.Log;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.RoutingActivity;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.NotificationsDatabase;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;

import java.io.IOException;
import java.util.List;

/**
 * Handles posting system notifications for new messages.
 *
 *
 * @author Moxie Marlinspike
 */

public class MessageNotifier {

  public static final String PATTERN_NONE    = "none";
  public static final String PATTERN_DEFAULT = "default";
  public static final String PATTERN_CUSTOM  = "custom";

  public static final int NOTIFICATION_ID = 1338;

  private volatile static long visibleThread = -1;

  public static void setVisibleThread(long threadId) {
    visibleThread = threadId;
  }

  public static void notifyMessageDeliveryFailed(Context context, Recipients recipients, long threadId) {
    if (visibleThread == threadId) {
      sendInThreadNotification(context);
    } else {
      Intent intent = new Intent(context, RoutingActivity.class);
      intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
      intent.putExtra("recipients", recipients);
      intent.putExtra("thread_id", threadId);
      intent.setData((Uri.parse("custom://"+System.currentTimeMillis())));

      NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
      builder.setSmallIcon(R.drawable.icon_notification);
      builder.setLargeIcon(BitmapFactory.decodeResource(context.getResources(),
                                                        R.drawable.ic_list_alert_sms_failed));
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
    updateNotification(context, masterSecret, false);
  }

  public static void updateNotification(Context context, MasterSecret masterSecret, long threadId) {
    if (visibleThread == threadId) {
      DatabaseFactory.getThreadDatabase(context).setRead(threadId);
      sendInThreadNotification(context);
    } else {
      updateNotification(context, masterSecret, true);
    }
  }

  private static void updateNotification(Context context, MasterSecret masterSecret, boolean signal) {
    Cursor cursor = null;

    try {
      cursor = DatabaseFactory.getMmsSmsDatabase(context).getUnread();

      if (cursor == null || cursor.isAfterLast()) {
        ((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE))
          .cancel(NOTIFICATION_ID);
        return;
      }

      NotificationState notificationState = constructNotificationState(context, masterSecret, cursor);

      if (notificationState.hasMultipleThreads()) {
        sendMultipleThreadNotification(context, masterSecret, notificationState, signal);
      } else {
        sendSingleThreadNotification(context, masterSecret, notificationState, signal);
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  private static void sendSingleThreadNotification(Context context,
                                                   MasterSecret masterSecret,
                                                   NotificationState notificationState,
                                                   boolean signal)
  {
    List<NotificationItem>notifications = notificationState.getNotifications();
    NotificationCompat.Builder builder  = new NotificationCompat.Builder(context);
    Recipient recipient                 = notifications.get(0).getIndividualRecipient();

    builder.setSmallIcon(R.drawable.icon_notification);
    builder.setLargeIcon(recipient.getContactPhoto());
    builder.setContentTitle(recipient.toShortString());
    builder.setContentText(notifications.get(0).getText());
    builder.setContentIntent(notifications.get(0).getPendingIntent(context));

    if (masterSecret != null) {
      builder.addAction(R.drawable.check, context.getString(R.string.MessageNotifier_mark_as_read),
                        notificationState.getMarkAsReadIntent(context, masterSecret));
    }

    SpannableStringBuilder content = new SpannableStringBuilder();

    for (NotificationItem item : notifications) {
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

    builder.setSmallIcon(R.drawable.icon_notification);
    builder.setLargeIcon(BitmapFactory.decodeResource(context.getResources(),
                                                      R.drawable.icon_notification));
    builder.setContentTitle(String.format(context.getString(R.string.MessageNotifier_d_new_messages),
                                          notificationState.getMessageCount()));
    builder.setContentText(String.format(context.getString(R.string.MessageNotifier_most_recent_from_s),
                                         notifications.get(0).getIndividualRecipientName()));
    builder.setContentIntent(PendingIntent.getActivity(context, 0, new Intent(context, RoutingActivity.class), 0));

    if (masterSecret != null) {
      builder.addAction(R.drawable.check, context.getString(R.string.MessageNotifier_mark_all_as_read),
                        notificationState.getMarkAsReadIntent(context, masterSecret));
    }

    InboxStyle style = new InboxStyle();

    for (NotificationItem item : notifications) {
      style.addLine(item.getTickerText());
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
      Cursor c = DatabaseFactory.getNotificationsDatabase(context).getDefaultNotification();
      String ringtone = c.getString(c.getColumnIndexOrThrow(NotificationsDatabase.SOUND));
      c.close();

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
      Recipient recipient   = record.getIndividualRecipient();
      Recipients recipients = record.getRecipients();
      long         threadId = record.getThreadId();
      SpannableString body  = record.getDisplayBody();
      Uri          image    = null;

      // XXXX This is so fucked up.  FIX ME!
      if (body.toString().equals(context.getString(R.string.MessageDisplayHelper_decrypting_please_wait))) {
        body = new SpannableString(context.getString(R.string.MessageNotifier_encrypted_message));
        body.setSpan(new StyleSpan(android.graphics.Typeface.ITALIC), 0, body.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      }

      notificationState.addNotification(new NotificationItem(recipient, recipients, threadId, body, image));
    }

    reader.close();
    return notificationState;
  }

  public static void setNotificationAlarms(Context context,
                                            NotificationCompat.Builder builder,
                                            boolean signal)
  {
    Cursor c = DatabaseFactory.getNotificationsDatabase(context).getDefaultNotification();

    final String one = "1";

    if ( ! one.equals(c.getString(c.getColumnIndexOrThrow(NotificationsDatabase.ENABLED))) )
    {
        return;
    }
    
    String sound                = c.getString(c.getColumnIndexOrThrow(NotificationsDatabase.SOUND));
    Boolean vibrate             = one.equals(c.getString(c.getColumnIndexOrThrow(NotificationsDatabase.VIBRATE)));
    String vibratePattern       = c.getString(c.getColumnIndexOrThrow(NotificationsDatabase.VIBRATE_PATTERN));
    String vibratePatternCustom = c.getString(c.getColumnIndexOrThrow(NotificationsDatabase.VIBRATE_PATTERN_CUSTOM));
    Boolean led                 = one.equals(c.getString(c.getColumnIndexOrThrow(NotificationsDatabase.LED)));
    String ledColor             = c.getString(c.getColumnIndexOrThrow(NotificationsDatabase.LED_COLOR));
    String ledPattern           = c.getString(c.getColumnIndexOrThrow(NotificationsDatabase.LED_PATTERN));
    String ledPatternCustom     = c.getString(c.getColumnIndexOrThrow(NotificationsDatabase.LED_PATTERN_CUSTOM));
    c.close();
    
    builder.setSound(TextUtils.isEmpty(sound) || !signal ? null : Uri.parse(sound));

    if (signal && vibrate)
      builder.setDefaults(Notification.DEFAULT_VIBRATE);

    if ( led )
    {
      long[] ledPatternArray;
      if (ledPattern.equals(PATTERN_CUSTOM)) {
        ledPatternArray = parsePattern(ledPatternCustom);
      }
      else {
        ledPatternArray = parsePattern(ledPattern);
      }
      builder.setLights(Color.parseColor(ledColor), (int)ledPatternArray[0], (int)ledPatternArray[1] );
    }

  }


  private static long[] parsePattern(String pattern) {

    if ( pattern == null || pattern.isEmpty() ||  !pattern.matches("[,0-9]+") )
    {
      long[] retval={0,0};
      return retval;
    }

    String [] strArray = pattern.split(",");

    long[] intArray = new long[ strArray.length ];

    for (int i=0; i< strArray.length; i++)
      intArray[i] = Integer.parseInt(strArray[i]);

    return intArray;
  }
}
