package org.thoughtcrime.securesms.notifications;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
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
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.model.MessageRecord;

import java.util.HashMap;
import java.util.List;

public class PebbleNotifier {

    public static void sendSmsNotification(Context context, MasterSecret masterSecret, long messageId, long threadId) {
        sendMessageNotification(context, masterSecret, messageId, threadId, false);
    }

    public static void sendMmsNotification(Context context, MasterSecret masterSecret, long messageId, long threadId) {
        sendMessageNotification(context, masterSecret, messageId, threadId, true);
    }

    private static void sendMessageNotification(Context context, MasterSecret masterSecret, long messageId, long threadId, boolean isMms) {
        if(MessageNotifier.getVisibleThread() == threadId)
            return;

        MessageRecord record;
        if((record = findMessage(context, masterSecret, messageId, threadId, isMms)) != null)
            sendNotification(context, record.getIndividualRecipient().toShortString(), record.getDisplayBody().toString());
    }

    private static MessageRecord findMessage(Context context, MasterSecret masterSecret, long messageId, long threadId, boolean isMms) {
        Cursor cursor = null;

        try {
            cursor = DatabaseFactory.getMmsSmsDatabase(context).getConversation(threadId);
            if (cursor == null || cursor.isAfterLast()) {
                return null;
            }

            MmsSmsDatabase.Reader reader;
            if (masterSecret == null) reader = DatabaseFactory.getMmsSmsDatabase(context).readerFor(cursor);
            else                      reader = DatabaseFactory.getMmsSmsDatabase(context).readerFor(cursor, masterSecret);

            MessageRecord record;
            while ((record = reader.getNext()) != null) {
                if(record.getId() == messageId && record.isMms() == isMms) break;
            }

            return record;


        } finally {
            if (cursor != null)
                cursor.close();
        }
    }

    public static void sendNotification(Context context, String title, String body) {
        if (!PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(ApplicationPreferencesActivity.NOTIFICATION_PREF, true))
        {
            return;
        }

        if (!PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(ApplicationPreferencesActivity.PEBBLE_NOTIFICATION_PREF, false))
        {
            return;
        }

        // Check that pebble app is installed and a watch is connected
        if(!isPebbleConnected(context)) return;

        final HashMap data = new HashMap();
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
