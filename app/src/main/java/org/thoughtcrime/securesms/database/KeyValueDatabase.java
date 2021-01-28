package org.thoughtcrime.securesms.database;

import android.app.Application;
import android.content.ContentValues;
import android.database.Cursor;

import androidx.annotation.NonNull;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteDatabaseHook;
import net.sqlcipher.database.SQLiteOpenHelper;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.crypto.DatabaseSecret;
import org.thoughtcrime.securesms.crypto.DatabaseSecretProvider;
import org.thoughtcrime.securesms.keyvalue.KeyValueDataSet;
import org.thoughtcrime.securesms.util.CursorUtil;
import org.thoughtcrime.securesms.util.SqlUtil;

import java.util.Collection;
import java.util.Map;

/**
 * Persists data for the {@link org.thoughtcrime.securesms.keyvalue.KeyValueStore}.
 *
 * This is it's own separate physical database, so it cannot do joins or queries with any other
 * tables.
 */
public class KeyValueDatabase extends SQLiteOpenHelper implements SignalDatabase {

  private static final String TAG = Log.tag(KeyValueDatabase.class);

  private static final int    DATABASE_VERSION = 1;
  private static final String DATABASE_NAME    = "signal-key-value.db";

  private static final String TABLE_NAME = "key_value";
  private static final String ID         = "_id";
  private static final String KEY        = "key";
  private static final String VALUE      = "value";
  private static final String TYPE       = "type";

  private static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + "(" + ID    + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                                                                  KEY   + " TEXT UNIQUE, " +
                                                                                  VALUE + " TEXT, " +
                                                                                  TYPE  + " INTEGER)";

  private static volatile KeyValueDatabase instance;

  private final Application    application;
  private final DatabaseSecret databaseSecret;

  public static @NonNull KeyValueDatabase getInstance(@NonNull Application context) {
    if (instance == null) {
      synchronized (KeyValueDatabase.class) {
        if (instance == null) {
          instance = new KeyValueDatabase(context, DatabaseSecretProvider.getOrCreateDatabaseSecret(context));
        }
      }
    }
    return instance;
  }

  public KeyValueDatabase(@NonNull Application application, @NonNull DatabaseSecret databaseSecret) {
    super(application, DATABASE_NAME, null, DATABASE_VERSION, new SqlCipherDatabaseHook());

    this.application    = application;
    this.databaseSecret = databaseSecret;
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    Log.i(TAG, "onCreate()");

    db.execSQL(CREATE_TABLE);

    if (DatabaseFactory.getInstance(application).hasTable("key_value")) {
      Log.i(TAG, "Found old key_value table. Migrating data.");
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

    if (DatabaseFactory.getInstance(application).hasTable("key_value")) {
      Log.i(TAG, "Dropping original key_value table from the main database.");
      DatabaseFactory.getInstance(application).getRawDatabase().rawExecSQL("DROP TABLE key_value");
    }
  }

  public @NonNull KeyValueDataSet getDataSet() {
    KeyValueDataSet dataSet = new KeyValueDataSet();

    try (Cursor cursor = getReadableDatabase().query(TABLE_NAME, null, null, null, null, null, null)){
      while (cursor != null && cursor.moveToNext()) {
        Type   type = Type.fromId(cursor.getInt(cursor.getColumnIndexOrThrow(TYPE)));
        String key  = cursor.getString(cursor.getColumnIndexOrThrow(KEY));

        switch (type) {
          case BLOB:
            dataSet.putBlob(key, cursor.getBlob(cursor.getColumnIndexOrThrow(VALUE)));
            break;
          case BOOLEAN:
            dataSet.putBoolean(key, cursor.getInt(cursor.getColumnIndexOrThrow(VALUE)) == 1);
            break;
          case FLOAT:
            dataSet.putFloat(key, cursor.getFloat(cursor.getColumnIndexOrThrow(VALUE)));
            break;
          case INTEGER:
            dataSet.putInteger(key, cursor.getInt(cursor.getColumnIndexOrThrow(VALUE)));
            break;
          case LONG:
            dataSet.putLong(key, cursor.getLong(cursor.getColumnIndexOrThrow(VALUE)));
            break;
          case STRING:
            dataSet.putString(key, cursor.getString(cursor.getColumnIndexOrThrow(VALUE)));
            break;
        }
      }
    }

    return dataSet;
  }

  public void writeDataSet(@NonNull KeyValueDataSet dataSet, @NonNull Collection<String> removes) {
    SQLiteDatabase db = getWritableDatabase();

    db.beginTransaction();
    try {
      for (Map.Entry<String, Object> entry : dataSet.getValues().entrySet()) {
        String key   = entry.getKey();
        Object value = entry.getValue();
        Class  type  = dataSet.getType(key);

        ContentValues contentValues = new ContentValues(3);
        contentValues.put(KEY, key);

        if (type == byte[].class) {
          contentValues.put(VALUE, (byte[]) value);
          contentValues.put(TYPE, Type.BLOB.getId());
        } else if (type == Boolean.class) {
          contentValues.put(VALUE, (boolean) value);
          contentValues.put(TYPE, Type.BOOLEAN.getId());
        } else if (type == Float.class) {
          contentValues.put(VALUE, (float) value);
          contentValues.put(TYPE, Type.FLOAT.getId());
        } else if (type == Integer.class) {
          contentValues.put(VALUE, (int) value);
          contentValues.put(TYPE, Type.INTEGER.getId());
        } else if (type == Long.class) {
          contentValues.put(VALUE, (long) value);
          contentValues.put(TYPE, Type.LONG.getId());
        } else if (type == String.class) {
          contentValues.put(VALUE, (String) value);
          contentValues.put(TYPE, Type.STRING.getId());
        } else {
          throw new AssertionError("Unknown type: " + type);
        }

        db.insertWithOnConflict(TABLE_NAME, null, contentValues, SQLiteDatabase.CONFLICT_REPLACE);
      }

      String deleteQuery = KEY + " = ?";
      for (String remove : removes) {
        db.delete(TABLE_NAME, deleteQuery, new String[] { remove });
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  private @NonNull SQLiteDatabase getReadableDatabase() {
    return getReadableDatabase(databaseSecret.asString());
  }

  private @NonNull SQLiteDatabase getWritableDatabase() {
    return getWritableDatabase(databaseSecret.asString());
  }

  @Override
  public @NonNull SQLiteDatabase getSqlCipherDatabase() {
    return getWritableDatabase();
  }

  private static void migrateDataFromPreviousDatabase(@NonNull SQLiteDatabase oldDb, @NonNull SQLiteDatabase newDb) {
    try (Cursor cursor = oldDb.rawQuery("SELECT * FROM key_value", null)) {
      while (cursor.moveToNext()) {
        int type = CursorUtil.requireInt(cursor, "type");
        ContentValues values = new ContentValues();
        values.put(KEY, CursorUtil.requireString(cursor, "key"));
        values.put(TYPE, type);

        switch (type) {
          case 0:
            values.put(VALUE, CursorUtil.requireBlob(cursor, "value"));
            break;
          case 1:
            values.put(VALUE, CursorUtil.requireBoolean(cursor, "value"));
            break;
          case 2:
            values.put(VALUE, CursorUtil.requireFloat(cursor, "value"));
            break;
          case 3:
            values.put(VALUE, CursorUtil.requireInt(cursor, "value"));
            break;
          case 4:
            values.put(VALUE, CursorUtil.requireLong(cursor, "value"));
            break;
          case 5:
            values.put(VALUE, CursorUtil.requireString(cursor, "value"));
            break;
        }

        newDb.insert(TABLE_NAME, null, values);
      }
    }
  }

  private enum Type {
    BLOB(0), BOOLEAN(1), FLOAT(2), INTEGER(3), LONG(4), STRING(5);

    final int id;

    Type(int id) {
      this.id = id;
    }

    public int getId() {
      return id;
    }

    public static Type fromId(int id) {
      return values()[id];
    }
  }
}
