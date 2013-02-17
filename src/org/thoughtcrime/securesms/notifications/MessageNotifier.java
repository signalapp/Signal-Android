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
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.BigTextStyle;
import android.support.v4.app.NotificationCompat.InboxStyle;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.Log;

import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.RoutingActivity;
import org.thoughtcrime.securesms.contacts.ContactPhotoFactory;
import org.thoughtcrime.securesms.crypto.MasterCipher;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MessageDisplayHelper;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.protocol.Prefix;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.InvalidMessageException;
import org.thoughtcrime.securesms.util.Util;

import java.io.IOException;
import java.util.List;

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
        sendMultipleThreadNotification(context, notificationState, signal);
      } else {
        sendSingleThreadNotification(context, notificationState, signal);
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  private static void sendSingleThreadNotification(Context context,
                                                   NotificationState notificationState,
                                                   boolean signal)
  {
    List<NotificationItem> notifications = notificationState.getNotifications();
    NotificationCompat.Builder builder   = new NotificationCompat.Builder(context);
    Recipients recipients                = notifications.get(0).getRecipients();

    builder.setSmallIcon(R.drawable.icon_notification);
    builder.setLargeIcon(recipients.getPrimaryRecipient().getContactPhoto());
    builder.setContentTitle(recipients.getPrimaryRecipient().toShortString());
    builder.setContentText(notifications.get(0).getText());
    builder.setContentIntent(notifications.get(0).getPendingIntent(context));

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
                                         notifications.get(0).getRecipientName()));
    builder.setContentIntent(PendingIntent.getActivity(context, 0, new Intent(context, RoutingActivity.class), 0));

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
      SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
      String ringtone      = sp.getString(ApplicationPreferencesActivity.RINGTONE_PREF, null);

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

    while (cursor.moveToNext()) {
      Recipients   recipients = getRecipients(context, cursor);
      long         threadId   = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.THREAD_ID));
      CharSequence body       = getBody(context, masterSecret, cursor);
      Uri          image      = null;

      notificationState.addNotification(new NotificationItem(recipients, threadId, body, image));
    }

    return notificationState;
  }

  private static CharSequence getBody(Context context, MasterSecret masterSecret, Cursor cursor) {
    String body = cursor.getString(cursor.getColumnIndexOrThrow(SmsDatabase.BODY));

    if (body == null) {
      return context.getString(R.string.MessageNotifier_no_subject);
    }

    if (masterSecret != null) {
      try {
        body = MessageDisplayHelper.getDecryptedMessageBody(new MasterCipher(masterSecret), body);
      } catch (InvalidMessageException e) {
        Log.w("MessageNotifier", e);
        return Util.getItalicizedString(context.getString(R.string.MessageNotifier_corrupted_ciphertext));
      }
    }

    if (body.startsWith(Prefix.SYMMETRIC_ENCRYPT) ||
        body.startsWith(Prefix.ASYMMETRIC_ENCRYPT) ||
        body.startsWith(Prefix.ASYMMETRIC_LOCAL_ENCRYPT))
    {
      return Util.getItalicizedString(context.getString(R.string.MessageNotifier_encrypted_message));
    } else if (body.startsWith(Prefix.KEY_EXCHANGE) ||
               body.startsWith(Prefix.PROCESSED_KEY_EXCHANGE))
    {
      return Util.getItalicizedString(context.getString(R.string.MessageNotifier_key_exchange));
    }

    return body;
  }

  private static Recipients getSmsRecipient(Context context, Cursor cursor)
      throws RecipientFormattingException
  {
    String address        = cursor.getString(cursor.getColumnIndexOrThrow(SmsDatabase.ADDRESS));
    return RecipientFactory.getRecipientsFromString(context, address, false);
  }

  private static Recipients getMmsRecipient(Context context, Cursor cursor)
      throws RecipientFormattingException
  {
    long messageId        = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.ID));
    String address        = DatabaseFactory.getMmsDatabase(context).getMessageRecipient(messageId);
    return RecipientFactory.getRecipientsFromString(context, address, false);
  }

  private static Recipients getRecipients(Context context, Cursor cursor) {
    String type = cursor.getString(cursor.getColumnIndexOrThrow(MmsSmsDatabase.TRANSPORT));

    try {
      if (type.equals("sms")) {
        return getSmsRecipient(context, cursor);
      } else {
        return getMmsRecipient(context, cursor);
      }
    } catch (RecipientFormattingException e) {
      Log.w("MessageNotifier", e);
      return new Recipients(new Recipient("Unknown", "Unknown", null,
                            ContactPhotoFactory.getDefaultContactPhoto(context)));
    }
  }

  private static void setNotificationAlarms(Context context,
                                            NotificationCompat.Builder builder,
                                            boolean signal)
  {
    SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);

    String ringtone              = sp.getString(ApplicationPreferencesActivity.RINGTONE_PREF, null);
    boolean vibrate              = sp.getBoolean(ApplicationPreferencesActivity.VIBRATE_PREF, true);
    String ledColor              = sp.getString(ApplicationPreferencesActivity.LED_COLOR_PREF, "green");
    String ledBlinkPattern       = sp.getString(ApplicationPreferencesActivity.LED_BLINK_PREF, "500,2000");
    String ledBlinkPatternCustom = sp.getString(ApplicationPreferencesActivity.LED_BLINK_PREF_CUSTOM, "500,2000");
    String[] blinkPatternArray   = parseBlinkPattern(ledBlinkPattern, ledBlinkPatternCustom);

    builder.setSound(TextUtils.isEmpty(ringtone) || !signal ? null : Uri.parse(ringtone));

    if (signal && vibrate)
      builder.setDefaults(Notification.DEFAULT_VIBRATE);

    builder.setLights(Color.parseColor(ledColor), Integer.parseInt(blinkPatternArray[0]),
                      Integer.parseInt(blinkPatternArray[1]));
  }

  private static String[] parseBlinkPattern(String blinkPattern, String blinkPatternCustom) {
    if (blinkPattern.equals("custom"))
      blinkPattern = blinkPatternCustom;

    return blinkPattern.split(",");
  }
}
