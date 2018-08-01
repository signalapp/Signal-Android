package org.thoughtcrime.securesms.database;


import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import net.sqlcipher.database.SQLiteDatabase;

import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.Base64;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECKeyPair;
import org.whispersystems.libsignal.ecc.ECPrivateKey;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class SignedPreKeyDatabase extends Database {

  private static final String TAG = SignedPreKeyDatabase.class.getSimpleName();

  public static final String TABLE_NAME = "signed_prekeys";

  private static final String ID          = "_id";
  public  static final String KEY_ID      = "key_id";
  public  static final String PUBLIC_KEY  = "public_key";
  public  static final String PRIVATE_KEY = "private_key";
  public  static final String SIGNATURE   = "signature";
  public  static final String TIMESTAMP   = "timestamp";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME +
      " (" + ID + " INTEGER PRIMARY KEY, " +
      KEY_ID + " INTEGER UNIQUE, " +
      PUBLIC_KEY + " TEXT NOT NULL, " +
      PRIVATE_KEY + " TEXT NOT NULL, " +
      SIGNATURE + " TEXT NOT NULL, " +
      TIMESTAMP + " INTEGER DEFAULT 0);";

  SignedPreKeyDatabase(Context context, SQLCipherOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  public @Nullable SignedPreKeyRecord getSignedPreKey(int keyId) {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();

    try (Cursor cursor = database.query(TABLE_NAME, null, KEY_ID + " = ?",
                                        new String[] {String.valueOf(keyId)},
                                        null, null, null))
    {
      if (cursor != null && cursor.moveToFirst()) {
        try {
          ECPublicKey  publicKey  = Curve.decodePoint(Base64.decode(cursor.getString(cursor.getColumnIndexOrThrow(PUBLIC_KEY))), 0);
          ECPrivateKey privateKey = Curve.decodePrivatePoint(Base64.decode(cursor.getString(cursor.getColumnIndexOrThrow(PRIVATE_KEY))));
          byte[]       signature  = Base64.decode(cursor.getString(cursor.getColumnIndexOrThrow(SIGNATURE)));
          long         timestamp  = cursor.getLong(cursor.getColumnIndexOrThrow(TIMESTAMP));

          return new SignedPreKeyRecord(keyId, timestamp, new ECKeyPair(publicKey, privateKey), signature);
        } catch (InvalidKeyException | IOException e) {
          Log.w(TAG, e);
        }
      }
    }

    return null;
  }

  public @NonNull List<SignedPreKeyRecord> getAllSignedPreKeys() {
    SQLiteDatabase           database = databaseHelper.getReadableDatabase();
    List<SignedPreKeyRecord> results  = new LinkedList<>();

    try (Cursor cursor = database.query(TABLE_NAME, null, null, null, null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        try {
          int          keyId      = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_ID));
          ECPublicKey  publicKey  = Curve.decodePoint(Base64.decode(cursor.getString(cursor.getColumnIndexOrThrow(PUBLIC_KEY))), 0);
          ECPrivateKey privateKey = Curve.decodePrivatePoint(Base64.decode(cursor.getString(cursor.getColumnIndexOrThrow(PRIVATE_KEY))));
          byte[]       signature  = Base64.decode(cursor.getString(cursor.getColumnIndexOrThrow(SIGNATURE)));
          long         timestamp  = cursor.getLong(cursor.getColumnIndexOrThrow(TIMESTAMP));

          results.add(new SignedPreKeyRecord(keyId, timestamp, new ECKeyPair(publicKey, privateKey), signature));
        } catch (InvalidKeyException | IOException e) {
          Log.w(TAG, e);
        }
      }
    }

    return results;
  }

  public void insertSignedPreKey(int keyId, SignedPreKeyRecord record) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();

    ContentValues contentValues = new ContentValues();
    contentValues.put(KEY_ID, keyId);
    contentValues.put(PUBLIC_KEY, Base64.encodeBytes(record.getKeyPair().getPublicKey().serialize()));
    contentValues.put(PRIVATE_KEY, Base64.encodeBytes(record.getKeyPair().getPrivateKey().serialize()));
    contentValues.put(SIGNATURE, Base64.encodeBytes(record.getSignature()));
    contentValues.put(TIMESTAMP, record.getTimestamp());

    database.replace(TABLE_NAME, null, contentValues);
  }


  public void removeSignedPreKey(int keyId) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    database.delete(TABLE_NAME, KEY_ID + " = ? AND " + SIGNATURE + " IS NOT NULL", new String[] {String.valueOf(keyId)});
  }

}
