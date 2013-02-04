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
package org.thoughtcrime.securesms.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;
import android.util.Log;

import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.ConversationListActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.recipients.Recipients;

import java.io.IOException;
import java.util.LinkedList;

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

  private static Bitmap buildContactPhoto(Recipients recipients) {
    Recipient recipient = recipients.getPrimaryRecipient();

    if (recipient == null) {
      return null;
    } else {
      return recipient.getContactPhoto();
    }
  }

  private static String buildTickerMessage(Context context, int count, Recipients recipients) {
    Recipient recipient   = recipients.getPrimaryRecipient();

    if (recipient == null) {
      return String.format(context.getString(R.string.MessageNotifier_d_new_messages), count);
    } else {
      return String.format(context.getString(R.string.MessageNotifier_d_new_messages_most_recent_from_s), count,
                           recipient.getName() == null ? recipient.getNumber() : recipient.getName());
    }
  }

  private static String buildTitleMessage(Context context, int count) {
    return String.format(context.getString(R.string.MessageNotifier_d_new_messages), count);
  }

  private static String buildSubtitleMessage(Context context, Recipients recipients) {
    Recipient recipient = recipients.getPrimaryRecipient();

    if (recipient != null) {
      return String.format(context.getString(R.string.MessageNotifier_most_recent_from_s),
                           (recipient.getName() == null ? recipient.getNumber() : recipient.getName()));
    }

    return null;
  }

  private static Recipients getSmsRecipient(Context context, Cursor c) throws RecipientFormattingException {
    String address        = c.getString(c.getColumnIndexOrThrow(SmsDatabase.ADDRESS));
    return RecipientFactory.getRecipientsFromString(context, address, false);
  }

  private static Recipients getMmsRecipient(Context context, Cursor c) throws RecipientFormattingException {
    long messageId        = c.getLong(c.getColumnIndexOrThrow(MmsDatabase.ID));
    String address        = DatabaseFactory.getMmsDatabase(context).getMessageRecipient(messageId);
    return RecipientFactory.getRecipientsFromString(context, address, false);
  }

  private static Recipients getMostRecentRecipients(Context context, Cursor c) {
    if (c != null && c.moveToLast()) {
      try {
        String type = c.getString(c.getColumnIndexOrThrow(MmsSmsDatabase.TRANSPORT));

        if (type.equals("sms"))
          return getSmsRecipient(context, c);
        else
          return getMmsRecipient(context, c);

      } catch (RecipientFormattingException e) {
        return new Recipients(new LinkedList<Recipient>());
      }
    }

    return null;
  }


  private static PendingIntent buildPendingIntent(Context context, Cursor c, Recipients recipients) {
    Intent intent = new Intent(context, ConversationListActivity.class);
    intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

    Log.w("SMSNotifier", "Building pending intent...");
    if (c != null && c.getCount() == 1) {
      Log.w("SMSNotifier", "Adding extras...");
      c.moveToLast();
      long threadId = c.getLong(c.getColumnIndexOrThrow(SmsDatabase.THREAD_ID));
      Log.w("SmsNotifier", "Adding thread_id to pending intent: " + threadId);

      if (recipients.getPrimaryRecipient() != null) {
        intent.putExtra("recipients", recipients);
        intent.putExtra("thread_id", threadId);
      }

      intent.setData((Uri.parse("custom://"+System.currentTimeMillis())));
    }

    return PendingIntent.getActivity(context, 0, intent, 0);
  }

  private static void sendNotification(Context context, NotificationManager manager,
                                       PendingIntent launchIntent, Bitmap contactPhoto,
                                       String ticker, String title,
                                       String subtitle, boolean signal)
  {
    SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
    if (!sp.getBoolean(ApplicationPreferencesActivity.NOTIFICATION_PREF, true)) return;

    String ringtone              = sp.getString(ApplicationPreferencesActivity.RINGTONE_PREF, null);
    boolean vibrate              = sp.getBoolean(ApplicationPreferencesActivity.VIBRATE_PREF, true);
    String ledColor              = sp.getString(ApplicationPreferencesActivity.LED_COLOR_PREF, "green");
    String ledBlinkPattern       = sp.getString(ApplicationPreferencesActivity.LED_BLINK_PREF, "500,2000");
    String ledBlinkPatternCustom = sp.getString(ApplicationPreferencesActivity.LED_BLINK_PREF_CUSTOM, "500,2000");
    String[] blinkPatternArray   = parseBlinkPattern(ledBlinkPattern, ledBlinkPatternCustom);

    NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
    builder.setSmallIcon(R.drawable.icon_notification);
    builder.setLargeIcon(contactPhoto);
    builder.setTicker(ticker);
    builder.setContentTitle(title);
    builder.setContentText(subtitle);
    builder.setContentIntent(launchIntent);
    builder.setSound(TextUtils.isEmpty(ringtone) || !signal ? null : Uri.parse(ringtone));

    if (signal && vibrate)
      builder.setDefaults(Notification.DEFAULT_VIBRATE);

    builder.setLights(Color.parseColor(ledColor), Integer.parseInt(blinkPatternArray[0]), Integer.parseInt(blinkPatternArray[1]));

    manager.notify(NOTIFICATION_ID, builder.build());
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

  private static void updateNotification(Context context, boolean signal) {
    NotificationManager manager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
    manager.cancel(NOTIFICATION_ID);

    Cursor c = null;

    try {
      c = DatabaseFactory.getMmsSmsDatabase(context).getUnread();

      if (c == null || !c.moveToFirst()) {
        return;
      }

      Recipients recipients      = getMostRecentRecipients(context, c);
      String ticker              = buildTickerMessage(context, c.getCount(), recipients);
      String title               = buildTitleMessage(context, c.getCount());
      String subtitle            = buildSubtitleMessage(context, recipients);
      PendingIntent launchIntent = buildPendingIntent(context, c, recipients);
      Bitmap contactPhoto        = buildContactPhoto(recipients);

      sendNotification(context, manager, launchIntent, contactPhoto,
                       ticker, title, subtitle, signal);
    } finally {
      if (c != null)
        c.close();
    }
  }

  public static void updateNotification(final Context context) {
    updateNotification(context, false);
  }

  public static void updateNotification(Context context, long threadId) {
    if (visibleThread == threadId) {
      DatabaseFactory.getThreadDatabase(context).setRead(threadId);
      sendInThreadNotification(context);
    } else {
      updateNotification(context, true);
    }
  }

  private static String[] parseBlinkPattern(String blinkPattern, String blinkPatternCustom) {
    if (blinkPattern.equals("custom"))
      blinkPattern = blinkPatternCustom;

    return blinkPattern.split(",");
  }
}
