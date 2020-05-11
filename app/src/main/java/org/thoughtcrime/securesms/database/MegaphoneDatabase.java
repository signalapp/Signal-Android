package org.thoughtcrime.securesms.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;

import net.sqlcipher.database.SQLiteDatabase;

import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;
import org.thoughtcrime.securesms.database.model.MegaphoneRecord;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.megaphone.Megaphones.Event;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * IMPORTANT: Writes should only be made through {@link org.thoughtcrime.securesms.megaphone.MegaphoneRepository}.
 */
public class MegaphoneDatabase extends Database {

  private static final String TAG = Log.tag(MegaphoneDatabase.class);

  private static final String TABLE_NAME = "megaphone";

  private static final String ID            = "_id";
  private static final String EVENT         = "event";
  private static final String SEEN_COUNT    = "seen_count";
  private static final String LAST_SEEN     = "last_seen";
  private static final String FIRST_VISIBLE = "first_visible";
  private static final String FINISHED      = "finished";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + "(" + ID               + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                                                                 EVENT            + " TEXT UNIQUE, " +
                                                                                 SEEN_COUNT       + " INTEGER, " +
                                                                                 LAST_SEEN        + " INTEGER, " +
                                                                                 FIRST_VISIBLE + " INTEGER, " +
                                                                                 FINISHED         + " INTEGER)";

  MegaphoneDatabase(Context context, SQLCipherOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  public void insert(@NonNull Collection<Event> events) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();

    db.beginTransaction();
    try {
      for (Event event : events) {
        ContentValues values = new ContentValues();
        values.put(EVENT, event.getKey());

        db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_IGNORE);
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public @NonNull List<MegaphoneRecord> getAllAndDeleteMissing() {
    SQLiteDatabase        db      = databaseHelper.getWritableDatabase();
    List<MegaphoneRecord> records = new ArrayList<>();

    db.beginTransaction();
    try {
      Set<String> missingKeys = new HashSet<>();

      try (Cursor cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME, null, null, null, null, null, null)) {
        while (cursor != null && cursor.moveToNext()) {
          String  event        = cursor.getString(cursor.getColumnIndexOrThrow(EVENT));
          int     seenCount    = cursor.getInt(cursor.getColumnIndexOrThrow(SEEN_COUNT));
          long    lastSeen     = cursor.getLong(cursor.getColumnIndexOrThrow(LAST_SEEN));
          long    firstVisible = cursor.getLong(cursor.getColumnIndexOrThrow(FIRST_VISIBLE));
          boolean finished     = cursor.getInt(cursor.getColumnIndexOrThrow(FINISHED)) == 1;

          if (Event.hasKey(event)) {
            records.add(new MegaphoneRecord(Event.fromKey(event), seenCount, lastSeen, firstVisible, finished));
          } else {
            Log.w(TAG, "No in-app handing for event '" + event + "'! Deleting it from the database.");
            missingKeys.add(event);
          }
        }
      }

      for (String missing : missingKeys) {
        String   query = EVENT + " = ?";
        String[] args  = new String[]{missing};

        databaseHelper.getWritableDatabase().delete(TABLE_NAME, query, args);
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }

    return records;
  }

  public void markFirstVisible(@NonNull Event event, long time) {
    String   query = EVENT + " = ?";
    String[] args  = new String[]{event.getKey()};

    ContentValues values = new ContentValues();
    values.put(FIRST_VISIBLE, time);

    databaseHelper.getWritableDatabase().update(TABLE_NAME, values, query, args);
  }

  public void markSeen(@NonNull Event event, int seenCount, long lastSeen) {
    String   query = EVENT + " = ?";
    String[] args  = new String[]{event.getKey()};

    ContentValues values = new ContentValues();
    values.put(SEEN_COUNT, seenCount);
    values.put(LAST_SEEN, lastSeen);

    databaseHelper.getWritableDatabase().update(TABLE_NAME, values, query, args);
  }

  public void markFinished(@NonNull Event event) {
    String   query = EVENT + " = ?";
    String[] args  = new String[]{event.getKey()};

    ContentValues values = new ContentValues();
    values.put(FINISHED, 1);

    databaseHelper.getWritableDatabase().update(TABLE_NAME, values, query, args);
  }

  public void delete(@NonNull Event event) {
    String   query = EVENT + " = ?";
    String[] args  = new String[]{event.getKey()};

    databaseHelper.getWritableDatabase().delete(TABLE_NAME, query, args);
  }
}
