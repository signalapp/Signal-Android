package org.whispersystems.textsecure.directory;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.util.Log;

import org.whispersystems.textsecure.push.ContactTokenDetails;
import org.whispersystems.textsecure.util.Base64;
import org.whispersystems.textsecure.util.PhoneNumberFormatter;
import org.whispersystems.textsecure.util.Util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Directory {

  private static final String DATABASE_NAME    = "whisper_directory.db";
  private static final int    DATABASE_VERSION = 1;

  private static final String TABLE_NAME   = "directory";
  private static final String ID           = "_id";
  private static final String TOKEN        = "token";
  private static final String REGISTERED   = "registered";
  private static final String RELAY        = "relay";
  private static final String SUPPORTS_SMS = "supports_sms";
  private static final String TIMESTAMP    = "timestamp";
  private static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + "(" + ID + " INTEGER PRIMARY KEY, " +
                                             TOKEN        + " TEXT UNIQUE, " +
                                             REGISTERED   + " INTEGER, " +
                                             RELAY        + " TEXT " +
                                             SUPPORTS_SMS + " INTEGER, " +
                                             TIMESTAMP    + " INTEGER);";

  private static Directory instance;

  public synchronized static Directory getInstance(Context context) {
    if (instance == null) {
      instance = new Directory(context);
    }

    return instance;
  }

  private final DatabaseHelper databaseHelper;
  private final Context        context;

  private Directory(Context context) {
    this.context        = context;
    this.databaseHelper = new DatabaseHelper(context, DATABASE_NAME, null, DATABASE_VERSION);
  }

  public boolean isActiveNumber(String e164number) throws NotInDirectoryException {
    if (e164number == null || e164number.length() == 0) {
      return false;
    }

    SQLiteDatabase db     = databaseHelper.getReadableDatabase();
    String         token  = getToken(e164number);
    Cursor         cursor = null;

    try {
      cursor = db.query(TABLE_NAME,
                        new String[] {REGISTERED}, TOKEN + " = ?",
                        new String[] {token}, null, null, null);

      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getInt(0) == 1;
      } else {
        throw new NotInDirectoryException();
      }

    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public String getRelay(String e164number) {
    String         token    = getToken(e164number);
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    Cursor         cursor   = null;

    try {
      cursor = database.query(TABLE_NAME, null, TOKEN + " = ?", new String[]{token}, null, null, null);

      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getString(cursor.getColumnIndexOrThrow(RELAY));
      }

      return null;
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public void setToken(ContactTokenDetails token, boolean active) {
    SQLiteDatabase db     = databaseHelper.getWritableDatabase();
    ContentValues  values = new ContentValues();
    values.put(TOKEN, token.getToken());
    values.put(RELAY, token.getRelay());
    values.put(REGISTERED, active ? 1 : 0);
    values.put(SUPPORTS_SMS, token.isSupportsSms() ? 1 : 0);
    values.put(TIMESTAMP, System.currentTimeMillis());
    db.replace(TABLE_NAME, null, values);
  }

  public void setTokens(List<ContactTokenDetails> activeTokens, Collection<String> inactiveTokens) {
    long timestamp    = System.currentTimeMillis();
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.beginTransaction();

    try {
      for (ContactTokenDetails token : activeTokens) {
        Log.w("Directory", "Adding active token: " + token);
        ContentValues values = new ContentValues();
        values.put(TOKEN, token.getToken());
        values.put(REGISTERED, 1);
        values.put(TIMESTAMP, timestamp);
        values.put(RELAY, token.getRelay());
        values.put(SUPPORTS_SMS, token.isSupportsSms() ? 1 : 0);
        db.replace(TABLE_NAME, null, values);
      }

      for (String token : inactiveTokens) {
        ContentValues values = new ContentValues();
        values.put(TOKEN, token);
        values.put(REGISTERED, 0);
        values.put(TIMESTAMP, timestamp);
        db.replace(TABLE_NAME, null, values);
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public Set<String> getPushEligibleContactTokens(String localNumber) {
    Uri         uri     = Phone.CONTENT_URI;
    Set<String> results = new HashSet<String>();
    Cursor      cursor  = null;

    try {
      cursor = context.getContentResolver().query(uri, new String[] {Phone.NUMBER}, null, null, null);

      while (cursor != null && cursor.moveToNext()) {
        String rawNumber = cursor.getString(0);

        if (rawNumber != null) {
          String e164Number = PhoneNumberFormatter.formatNumber(rawNumber, localNumber);
          results.add(getToken(e164Number));
        }
      }

      if (cursor != null)
        cursor.close();

      cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME, new String[] {TOKEN},
                                                          null, null, null, null, null);

      while (cursor != null && cursor.moveToNext()) {
        results.add(cursor.getString(0));
      }

      return results;
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public String getToken(String e164number) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA1");
      byte[]        token  = Util.trim(digest.digest(e164number.getBytes()), 10);
      return Base64.encodeBytesWithoutPadding(token);
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  private class DatabaseHelper extends SQLiteOpenHelper {

    public DatabaseHelper(Context context, String name,
                          SQLiteDatabase.CursorFactory factory,
                          int version)
    {
      super(context, name, factory, version);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
      db.execSQL(CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }
  }

}
