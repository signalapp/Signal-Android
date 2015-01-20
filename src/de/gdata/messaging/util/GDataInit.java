package de.gdata.messaging.util;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

public class GDataInit {
    public static void init(Context context) {
        Cursor cursor = context.getContentResolver().query(
                Uri.parse("content://de.gdata.mobilesecurity.messaging.RegistrationContentProvider/details"),
                new String[] {}, null, null, null);

        // TODO: remove debug output
        // TODO: save current state to preferences
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    Log.d("GDATA", "ID: " + cursor.getInt(cursor.getColumnIndex("_id")));
                    Log.d("GDATA", "Value: " + cursor.getString(cursor.getColumnIndex("premium")));
                } while (cursor.moveToNext());
            }
            Log.d("GDATA", "Count: " + cursor.getCount());

            cursor.close();
        }
    }
}
