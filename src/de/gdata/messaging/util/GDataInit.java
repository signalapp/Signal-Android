package de.gdata.messaging.util;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

public class GDataInit {
    private static final String REGISTRATION_DATA_CONTENT_URI_STRING = "content://de.gdata.mobilesecurity.messaging.RegistrationContentProvider/details";
    private static final Uri REGISTRATION_DATA_CONTENT_URI = Uri.parse(REGISTRATION_DATA_CONTENT_URI_STRING);

    public static void init(Context context) {
        GDataPreferences preferences = new GDataPreferences(context);
        Cursor cursor = context.getContentResolver().query(REGISTRATION_DATA_CONTENT_URI, new String[] {}, null, null,
                null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    preferences.setPremiumInstalled(Boolean.parseBoolean(cursor.getString(cursor
                            .getColumnIndex("premium"))));
                } while (cursor.moveToNext());
            }
            cursor.close();
        }

        Log.d("GDATA", "Premium installed: " + preferences.isPremiumInstalled());
    }
}
