package org.thoughtcrime.securesms.database;

import android.app.Application;
import android.content.ContentValues;
import android.database.Cursor;

import androidx.annotation.NonNull;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteOpenHelper;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.crypto.DatabaseSecret;
import org.thoughtcrime.securesms.crypto.DatabaseSecretProvider;
import org.thoughtcrime.securesms.database.model.MegaphoneRecord;
import org.thoughtcrime.securesms.megaphone.Megaphones.Event;
import org.thoughtcrime.securesms.util.CursorUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * IMPORTANT: Writes should only be made through {@link org.thoughtcrime.securesms.megaphone.MegaphoneRepository}.
 */
public class MegaphoneDatabase extends SQLiteOpenHelper implements SignalDatabase {

  private static final String TAG = Log.tag(MegaphoneDatabase.class);

  private static final int    DATABASE_VERSION = 1;
  private static final String DATABASE_NAME    = "signal-megaphone.db";

  private static final String TABLE_NAME    = "megaphone";
  private static final String ID            = "_id";
  private static final String EVENT         = "event";
  private static final String SEEN_COUNT    = "seen_count";
  private static final String LAST_SEEN     = "last_seen";
  private static final String FIRST_VISIBLE = "first_visible";
  private static final String FINISHED      = "finished";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + "(" + ID            + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                                                                 EVENT         + " TEXT UNIQUE, " +
                                                                                 SEEN_COUNT    + " INTEGER, " +
                                                                                 LAST_SEEN     + " INTEGER, " +
                                                                                 FIRST_VISIBLE + " INTEGER, " +
                                                                                 FINISHED      + " INTEGER)";

  private static volatile MegaphoneDatabase instance;

  private final Application    application;
  private final DatabaseSecret databaseSecret;

  public static @NonNull MegaphoneDatabase getInstance(@NonNull Application context) {
    if (instance == null) {
      synchronized (MegaphoneDatabase.class) {
        if (instance == null) {
          instance = new MegaphoneDatabase(context, DatabaseSecretProvider.getOrCreateDatabaseSecret(context));
        }
      }
    }
    return instance;
  }

  public MegaphoneDatabase(@NonNull Application application, @NonNull DatabaseSecret databaseSecret) {
    super(application, DATABASE_NAME, null, DATABASE_VERSION, new SqlCipherDatabaseHook());

    this.application    = application;
    this.databaseSecret = databaseSecret;
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    Log.i(TAG, "onCreate()");

    db.execSQL(CREATE_TABLE);

    if (DatabaseFactory.getInstance(application).hasTable("megaphone")) {
      Log.i(TAG, "Found old megaphone table. Migrating data.");
      migrateDataFromPreviousDatabase(DatabaseFactory.getInstance(application).getRawDatabase(), db);
    }
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    Log.i(TAG, "onUpgrade(" + oldVersion + ", " + newVersion + ")");
  }

  @Override
  public void onOpen(SQLiteDatabase db) {
    Log.i(TAG, "onOpen()");

    if (DatabaseFactory.getInstance(application).hasTable("megaphone")) {
      Log.i(TAG, "Dropping original megaphone table from the main database.");
      DatabaseFactory.getInstance(application).getRawDatabase().rawExecSQL("DROP TABLE megaphone");
    }
  }

  public void insert(@NonNull Collection<Event> events) {
    SQLiteDatabase db = getWritableDatabase();

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
    SQLiteDatabase        db      = getWritableDatabase();
    List<MegaphoneRecord> records = new ArrayList<>();

    db.beginTransaction();
    try {
      Set<String> missingKeys = new HashSet<>();

      try (Cursor cursor = db.query(TABLE_NAME, null, null, null, null, null, null)) {
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

        db.delete(TABLE_NAME, query, args);
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

    getWritableDatabase().update(TABLE_NAME, values, query, args);
  }

  public void markSeen(@NonNull Event event, int seenCount, long lastSeen) {
    String   query = EVENT + " = ?";
    String[] args  = new String[]{event.getKey()};

    ContentValues values = new ContentValues();
    values.put(SEEN_COUNT, seenCount);
    values.put(LAST_SEEN, lastSeen);

    getWritableDatabase().update(TABLE_NAME, values, query, args);
  }

  public void markFinished(@NonNull Event event) {
    String   query = EVENT + " = ?";
    String[] args  = new String[]{event.getKey()};

    ContentValues values = new ContentValues();
    values.put(FINISHED, 1);

    getWritableDatabase().update(TABLE_NAME, values, query, args);
  }

  public void delete(@NonNull Event event) {
    String   query = EVENT + " = ?";
    String[] args  = new String[]{event.getKey()};

    getWritableDatabase().delete(TABLE_NAME, query, args);
  }

  private @NonNull SQLiteDatabase getWritableDatabase() {
    return getWritableDatabase(databaseSecret.asString());
  }

  @Override
  public @NonNull SQLiteDatabase getSqlCipherDatabase() {
    return getWritableDatabase();
  }

  private static void migrateDataFromPreviousDatabase(@NonNull SQLiteDatabase oldDb, @NonNull SQLiteDatabase newDb) {
    try (Cursor cursor = oldDb.rawQuery("SELECT * FROM megaphone", null)) {
      while (cursor.moveToNext()) {
        ContentValues values = new ContentValues();

        values.put(EVENT, CursorUtil.requireString(cursor, "event"));
        values.put(SEEN_COUNT, CursorUtil.requireInt(cursor, "seen_count"));
        values.put(LAST_SEEN, CursorUtil.requireLong(cursor, "last_seen"));
        values.put(FIRST_VISIBLE, CursorUtil.requireLong(cursor, "first_visible"));
        values.put(FINISHED, CursorUtil.requireInt(cursor, "finished"));

        newDb.insert(TABLE_NAME, null, values);
      }
    }
  }
}
