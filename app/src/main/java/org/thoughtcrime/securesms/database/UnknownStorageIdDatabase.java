package org.thoughtcrime.securesms.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.SqlUtil;
import org.whispersystems.libsignal.util.guava.Preconditions;
import org.whispersystems.signalservice.api.storage.SignalStorageRecord;
import org.whispersystems.signalservice.api.storage.StorageId;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A list of storage keys whose types we do not currently have syncing logic for. We need to
 * remember that these keys exist so that we don't blast any data away.
 */
public class UnknownStorageIdDatabase extends Database {

  private static final String TABLE_NAME = "storage_key";
  private static final String ID         = "_id";
  private static final String TYPE       = "type";
  private static final String STORAGE_ID = "key";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + ID         + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                                                                  TYPE       + " INTEGER, " +
                                                                                  STORAGE_ID + " TEXT UNIQUE)";

  public static final String[] CREATE_INDEXES = new String[] {
      "CREATE INDEX IF NOT EXISTS storage_key_type_index ON " + TABLE_NAME + " (" + TYPE + ");"
  };

  public UnknownStorageIdDatabase(Context context, SignalDatabase databaseHelper) {
    super(context, databaseHelper);
  }

  public List<StorageId> getAllUnknownIds() {
    List<StorageId> keys  = new ArrayList<>();

    String   query = TYPE + " > ?";
    String[] args  = SqlUtil.buildArgs(StorageId.largestKnownType());

    try (Cursor cursor = databaseHelper.getSignalReadableDatabase().query(TABLE_NAME, null, query, args, null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        String keyEncoded = cursor.getString(cursor.getColumnIndexOrThrow(STORAGE_ID));
        int    type       = cursor.getInt(cursor.getColumnIndexOrThrow(TYPE));
        try {
          keys.add(StorageId.forType(Base64.decode(keyEncoded), type));
        } catch (IOException e) {
          throw new AssertionError(e);
        }
      }
    }

    return keys;
  }

  public @Nullable SignalStorageRecord getById(@NonNull byte[] rawId) {
    String   query = STORAGE_ID + " = ?";
    String[] args  = new String[] { Base64.encodeBytes(rawId) };

    try (Cursor cursor = databaseHelper.getSignalReadableDatabase().query(TABLE_NAME, null, query, args, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        int type = cursor.getInt(cursor.getColumnIndexOrThrow(TYPE));
        return SignalStorageRecord.forUnknown(StorageId.forType(rawId, type));
      } else {
        return null;
      }
    }
  }

  public void applyStorageSyncUpdates(@NonNull Collection<SignalStorageRecord> inserts,
                                      @NonNull Collection<SignalStorageRecord> deletes)
  {
    SQLiteDatabase db = databaseHelper.getSignalWritableDatabase();

    db.beginTransaction();
    try {
      insert(inserts);
      delete(Stream.of(deletes).map(SignalStorageRecord::getId).toList());

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public void insert(@NonNull Collection<SignalStorageRecord> inserts) {
    SQLiteDatabase db = databaseHelper.getSignalWritableDatabase();

    Preconditions.checkArgument(db.inTransaction(), "Must be in a transaction!");

    for (SignalStorageRecord insert : inserts) {
      ContentValues values = new ContentValues();
      values.put(TYPE, insert.getType());
      values.put(STORAGE_ID, Base64.encodeBytes(insert.getId().getRaw()));

      db.insert(TABLE_NAME, null, values);
    }
  }

  public void delete(@NonNull Collection<StorageId> deletes) {
    SQLiteDatabase db          = databaseHelper.getSignalWritableDatabase();
    String         deleteQuery = STORAGE_ID + " = ?";

    Preconditions.checkArgument(db.inTransaction(), "Must be in a transaction!");

    for (StorageId id : deletes) {
      String[] args = SqlUtil.buildArgs(Base64.encodeBytes(id.getRaw()));
      db.delete(TABLE_NAME, deleteQuery, args);
    }
  }

  public void deleteByType(int type) {
    String   query = TYPE + " = ?";
    String[] args  = new String[]{String.valueOf(type)};

    databaseHelper.getSignalWritableDatabase().delete(TABLE_NAME, query, args);
  }

  public void deleteAll() {
    databaseHelper.getSignalWritableDatabase().delete(TABLE_NAME, null, null);
  }
}
