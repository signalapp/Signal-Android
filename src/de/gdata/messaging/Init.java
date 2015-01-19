package de.gdata.messaging;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

public class Init {
    public static void init(Context context) {
        Cursor c = context.getContentResolver().query(
                Uri.parse("content://de.gdata.mobilesecurity.messaging.RegistrationContentProvider/details"),
                new String[] {}, null, null, null);

        //TODO: remove debug output
        //TODO: save current state to preferences
        if (c.moveToFirst()) {
            do {
                Log.d("GDATA", "ID: " + c.getInt(c.getColumnIndex("_id")));
                Log.d("GDATA", "Value: " + c.getString(c.getColumnIndex("premium")));
            } while (c.moveToNext());
        }
        Log.d("GDATA", "Count: " + c.getCount());

        c.close();
     }
}
