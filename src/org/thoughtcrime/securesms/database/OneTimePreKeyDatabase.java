package org.thoughtcrime.securesms.database;


import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.support.annotation.Nullable;
import org.thoughtcrime.securesms.logging.Log;

import net.sqlcipher.database.SQLiteDatabase;

import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;
import org.thoughtcrime.securesms.util.Base64;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECKeyPair;
import org.whispersystems.libsignal.ecc.ECPrivateKey;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.state.PreKeyRecord;

import java.io.IOException;

public class OneTimePreKeyDatabase extends Database {

  private static final String TAG = OneTimePreKeyDatabase.class.getSimpleName();

  public  static final String TABLE_NAME  = "one_time_prekeys";
  private static final String ID          = "_id";
  public  static final String KEY_ID      = "key_id";
  public  static final String PUBLIC_KEY  = "public_key";
  public  static final String PRIVATE_KEY = "private_key";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME +
      " (" + ID + " INTEGER PRIMARY KEY, " +
      KEY_ID + " INTEGER UNIQUE, " +
      PUBLIC_KEY + " TEXT NOT NULL, " +
      PRIVATE_KEY + " TEXT NOT NULL);";

  OneTimePreKeyDatabase(Context context, SQLCipherOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  public @Nullable PreKeyRecord getPreKey(int keyId) {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();

    try (Cursor cursor = database.query(TABLE_NAME, null, KEY_ID + " = ?",
                                        new String[] {String.valueOf(keyId)},
                                        null, null, null))
    {
      if (cursor != null && cursor.moveToFirst()) {
        try {
          ECPublicKey  publicKey  = Curve.decodePoint(Base64.decode(cursor.getString(cursor.getColumnIndexOrThrow(PUBLIC_KEY))), 0);
          ECPrivateKey privateKey = Curve.decodePrivatePoint(Base64.decode(cursor.getString(cursor.getColumnIndexOrThrow(PRIVATE_KEY))));

          return new PreKeyRecord(keyId, new ECKeyPair(publicKey, privateKey));
        } catch (InvalidKeyException | IOException e) {
          Log.w(TAG, e);
        }
      }
    }

    return null;
  }

  public void insertPreKey(int keyId, PreKeyRecord record) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();

    ContentValues contentValues = new ContentValues();
    contentValues.put(KEY_ID, keyId);
    contentValues.put(PUBLIC_KEY, Base64.encodeBytes(record.getKeyPair().getPublicKey().serialize()));
    contentValues.put(PRIVATE_KEY, Base64.encodeBytes(record.getKeyPair().getPrivateKey().serialize()));

    database.replace(TABLE_NAME, null, contentValues);
  }

  public void removePreKey(int keyId) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    database.delete(TABLE_NAME, KEY_ID + " = ?", new String[] {String.valueOf(keyId)});
  }

}
