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

import java.util.LinkedList;

import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.SecureSMS;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.recipients.Recipients;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

/**
 * Handles posting system notifications for new messages.
 * 
 * 
 * @author Moxie Marlinspike
 */

public class MessageNotifier {

  public static final int NOTIFICATION_ID = 1338;
	
  private static String buildTickerMessage(int count, Recipients recipients) {
    Recipient recipient   = recipients.getPrimaryRecipient();
    StringBuilder builder = new StringBuilder();
    builder.append('(');
    builder.append(count);
    builder.append(')');
    builder.append(" New messages");
		
    if (recipient != null) {
      builder.append(", most recent from: ");				
      builder.append(recipient.getName() == null ? recipient.getNumber() : recipient.getName());
    }
		
    return builder.toString();
  }
	
  private static String buildTitleMessage(int count) {
    return "(" + count + ") New Messages";
  }
	
  private static String buildSubtitleMessage(Recipients recipients) {
    Recipient recipient = recipients.getPrimaryRecipient();
		
    if (recipient != null) {
      return "Most recent from: " + (recipient.getName() == null ? recipient.getNumber() : recipient.getName());
    }
		
    return null;
  }

  private static Recipients getSmsRecipient(Context context, Cursor c) throws RecipientFormattingException {
    String address        = c.getString(c.getColumnIndexOrThrow(SmsDatabase.ADDRESS));
    return RecipientFactory.getRecipientsFromString(context, address);
  }
	
  private static Recipients getMmsRecipient(Context context, Cursor c) throws RecipientFormattingException {
    long messageId        = c.getLong(c.getColumnIndexOrThrow(MmsDatabase.ID));
    String address        = DatabaseFactory.getMmsDatabase(context).getMessageRecipient(messageId);
    return RecipientFactory.getRecipientsFromString(context, address);
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
    Intent intent = new Intent(context, SecureSMS.class);
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

  private static void sendNotification(Context context, NotificationManager manager, PendingIntent launchIntent, 
				       String ticker, String title, String subtitle, boolean signal) 
  {
    SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);		
    if (!sp.getBoolean(ApplicationPreferencesActivity.NOTIFICATION_PREF, true)) return;
		
    Notification notification    = new Notification(R.drawable.stat_notify_sms, ticker, System.currentTimeMillis());
    String ringtone              = sp.getString(ApplicationPreferencesActivity.RINGTONE_PREF, null);
    boolean vibrate              = sp.getBoolean(ApplicationPreferencesActivity.VIBRATE_PREF, true);
    String ledColor              = sp.getString(ApplicationPreferencesActivity.LED_COLOR_PREF, "green");
    String ledBlinkPattern       = sp.getString(ApplicationPreferencesActivity.LED_BLINK_PREF, "500,2000");
    String ledBlinkPatternCustom = sp.getString(ApplicationPreferencesActivity.LED_BLINK_PREF_CUSTOM, "500,2000");
    String[] blinkPatternArray   = parseBlinkPattern(ledBlinkPattern, ledBlinkPatternCustom);
		
    notification.setLatestEventInfo(context, title, subtitle, launchIntent);
    notification.sound          = TextUtils.isEmpty(ringtone) || !signal ? null : Uri.parse(ringtone);
    if (signal && vibrate)
      notification.defaults  |= Notification.DEFAULT_VIBRATE;

    notification.flags         |= Notification.FLAG_SHOW_LIGHTS;
    notification.ledARGB        = Color.parseColor(ledColor);//0xff00ff00;
    notification.ledOnMS        = Integer.parseInt(blinkPatternArray[0]);
    notification.ledOffMS       = Integer.parseInt(blinkPatternArray[1]);
		
    manager.notify(NOTIFICATION_ID, notification);		
  }
	
  private static void flashNotification(Context context, NotificationManager manager) {
    sendNotification(context, manager, buildPendingIntent(context, null, null), "(1) New Messages", "(1) New Messages", null, true);
  }
	
  public static void updateNotification(Context context, boolean signal) {
    NotificationManager manager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
    manager.cancel(NOTIFICATION_ID);		
		
    Cursor c = null;
		
    try {
      c = DatabaseFactory.getMmsSmsDatabase(context).getUnread();
			
      if      ((c == null && signal) || (!c.moveToFirst() && signal)) {flashNotification(context, manager); return;}
      else if (c == null || !c.moveToFirst())                         return;
			
      Recipients recipients      = getMostRecentRecipients(context, c);
      String ticker              = buildTickerMessage(c.getCount(), recipients);
      String title               = buildTitleMessage(c.getCount());
      String subtitle            = buildSubtitleMessage(recipients);
      PendingIntent launchIntent = buildPendingIntent(context, c, recipients);
			
      sendNotification(context, manager, launchIntent, ticker, title, subtitle, signal);
    } finally {
      if (c != null)
	c.close();
    }
  }	
	
  private static String[] parseBlinkPattern(String blinkPattern, String blinkPatternCustom) {
    if (blinkPattern.equals("custom"))
      blinkPattern = blinkPatternCustom;
		
    return blinkPattern.split(",");
  }
}
