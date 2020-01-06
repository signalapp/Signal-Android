package org.thoughtcrime.securesms.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.sqlcipher.database.SQLiteDatabase;

import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;
import org.thoughtcrime.securesms.util.Base64;
import org.whispersystems.signalservice.api.storage.SignalStorageRecord;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * A list of storage keys whose types we do not currently have syncing logic for. We need to
 * remember that these keys exist so that we don't blast any data away.
 */
public class StorageKeyDatabase extends Database {

  private static final String TABLE_NAME = "storage_key";
  private static final String ID         = "_id";
  private static final String TYPE       = "type";
  private static final String KEY        = "key";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + ID   + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                                                                  TYPE + " INTEGER, " +
                                                                                  KEY  + " TEXT UNIQUE)";

  public static final String[] CREATE_INDEXES = new String[] {
      "CREATE INDEX IF NOT EXISTS storage_key_type_index ON " + TABLE_NAME + " (" + TYPE + ");"
  };

  public StorageKeyDatabase(Context context, SQLCipherOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  public List<byte[]> getAllKeys() {
    List<byte[]> keys = new ArrayList<>();

    try (Cursor cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME, null, null, null, null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        String keyEncoded = cursor.getString(cursor.getColumnIndexOrThrow(KEY));
        try {
          keys.add(Base64.decode(keyEncoded));
        } catch (IOException e) {
          throw new AssertionError(e);
        }
      }
    }

    return keys;
  }

  public @Nullable SignalStorageRecord getByKey(@NonNull byte[] key) {
    String   query = KEY + " = ?";
    String[] args  = new String[] { Base64.encodeBytes(key) };

    try (Cursor cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME, null, query, args, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        int type = cursor.getInt(cursor.getColumnIndexOrThrow(TYPE));
        return SignalStorageRecord.forUnknown(key, type);
      } else {
        return null;
      }
    }
  }

  public void applyStorageSyncUpdates(@NonNull Collection<SignalStorageRecord> inserts,
                                      @NonNull Collection<SignalStorageRecord> deletes)
  {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();

    db.beginTransaction();
    try {
      for (SignalStorageRecord insert : inserts) {
        ContentValues values = new ContentValues();
        values.put(TYPE, insert.getType());
        values.put(KEY, Base64.encodeBytes(insert.getKey()));

        db.insert(TABLE_NAME, null, values);
      }

      String deleteQuery = KEY + " = ?";

      for (SignalStorageRecord delete : deletes) {
        String[] args = new String[] { Base64.encodeBytes(delete.getKey()) };
        db.delete(TABLE_NAME, deleteQuery, args);
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }

  }

  public void deleteByType(int type) {
    String   query = TYPE + " = ?";
    String[] args  = new String[]{String.valueOf(type)};

    databaseHelper.getWritableDatabase().delete(TABLE_NAME, query, args);
  }

  public void deleteAll() {
    databaseHelper.getWritableDatabase().delete(TABLE_NAME, null, null);
  }
}
