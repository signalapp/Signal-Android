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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;
import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.EncryptingSmsDatabase;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.model.MessageRecord;

import java.util.Collections;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;

/**
 * Handles sending notifications to Pebble watches.
 *
 *
 * @author Edward Barnard
 */
public class PebbleNotifier {

  private static Set<Long> suppressedSmsIds;

  static {
    suppressedSmsIds = Collections.synchronizedSet(new HashSet<Long>());
  }

  public static void suppressNotificationForSms(long messageId) {
    suppressedSmsIds.add(messageId);
  }

  public static void sendSmsNotification(Context context, MasterSecret masterSecret, long messageId) {
    if(!isPebbleNotificationEnabled(context)) return;
    if(suppressedSmsIds.contains(messageId)) {
      suppressedSmsIds.remove(messageId);
      return;
    }

    EncryptingSmsDatabase database = DatabaseFactory.getEncryptingSmsDatabase(context);
    EncryptingSmsDatabase.Reader reader = database.getMessage(masterSecret, messageId);

    MessageRecord message = reader.getNext();

    sendMessageNotification(context, masterSecret, message);
  }

  public static void sendMmsNotification(Context context, MasterSecret masterSecret, long messageId) {
    if(!isPebbleNotificationEnabled(context)) return;

    MmsDatabase database = DatabaseFactory.getMmsDatabase(context);
    MmsDatabase.Reader reader = database.getMessage(masterSecret, messageId);

    MessageRecord message = reader.getNext();

    sendMessageNotification(context, masterSecret, message);
  }

  public static void sendMessageNotification(Context context, MasterSecret masterSecret, MessageRecord message) {
    if(message == null) return;
    if(masterSecret != null && message.isDecryptInProgress()) return;
    if(message.getThreadId() == MessageNotifier.getVisibleThread()) return;

    sendNotification(context, message.getIndividualRecipient().toShortString(), message.getDisplayBody().toString());
  }

  public static boolean isPebbleNotificationEnabled(Context context) {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

    if (!preferences.getBoolean(ApplicationPreferencesActivity.PEBBLE_NOTIFICATION_PREF, false))
      return false;

    if (!preferences.getBoolean(ApplicationPreferencesActivity.NOTIFICATION_PREF, true))
      return false;

    return true;
  }

  public static void sendNotification(Context context, String title, String body) {
    // Check that pebble app is installed and a watch is connected
    if(!isPebbleConnected(context) || !isPebbleNotificationEnabled(context)) return;

    final HashMap<String, String> data = new HashMap<String, String>();
    data.put("title", title);
    data.put("body", body);
    JSONObject jsonData = new JSONObject(data);
    String notificationData = new JSONArray().put(jsonData).toString();

    final Intent i = new Intent("com.getpebble.action.SEND_NOTIFICATION");

    i.putExtra("messageType", "PEBBLE_ALERT");
    i.putExtra("sender", context.getResources().getString(R.string.app_name));
    i.putExtra("notificationData", notificationData);

    context.sendBroadcast(i);

    Log.i("PebbleNotifier", "Pebble notification sent.");
  }

  public static boolean isPebbleAppInstalled(final Context context) {
    Cursor c = context.getContentResolver().query(Uri.parse("content://com.getpebble.android.provider/state"),
            null, null, null, null);
    if (c == null || !c.moveToNext()) {
      return false;
    }
    return true;
  }

  // Code taken from PebbleKit
  public static boolean isPebbleConnected(final Context context) {
    Cursor c = context.getContentResolver().query(Uri.parse("content://com.getpebble.android.provider/state"),
            null, null, null, null);
    if (c == null || !c.moveToNext()) {
      return false;
    }
    return c.getInt(0) == 1;
  }
}
