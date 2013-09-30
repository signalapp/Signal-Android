package org.whispersystems.textsecure.directory;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.util.Log;

import org.whispersystems.textsecure.util.Base64;
import org.whispersystems.textsecure.util.PhoneNumberFormatter;
import org.whispersystems.textsecure.util.Util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class Directory {

  private static final String DATABASE_NAME    = "whisper_directory.db";
  private static final int    DATABASE_VERSION = 1;

  private static final String TABLE_NAME   = "directory";
  private static final String ID           = "_id";
  private static final String TOKEN        = "token";
  private static final String REGISTERED   = "registered";
  private static final String SUPPORTS_SMS = "supports_sms";
  private static final String TIMESTAMP    = "timestamp";
  private static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + "(" + ID + " INTEGER PRIMARY KEY, " +
                                             TOKEN + " TEXT UNIQUE, " + REGISTERED + " INTEGER, " +
                                             SUPPORTS_SMS + " INTEGER, " + TIMESTAMP + " INTEGER);";

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

  public boolean containsNumbers(List<String> e164numbers) {
    if (e164numbers == null || e164numbers.isEmpty()) {
      return false;
    }

    for (String e164number : e164numbers) {
      if (!containsNumber(e164number))
        return false;
    }

    return true;
  }

  public boolean containsNumber(String e164number) {
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
      }

      return false;
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public void setActiveTokens(List<String> tokens) {
    long timestamp    = System.currentTimeMillis();
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.beginTransaction();

    try {
      ContentValues clear = new ContentValues();
      clear.put(REGISTERED, 0);
      clear.put(TIMESTAMP, timestamp);
      db.update(TABLE_NAME, clear, null, null);


      for (String token : tokens) {
        Log.w("Directory", "Adding token: " + token);
        ContentValues values = new ContentValues();
        values.put(TOKEN, token);
        values.put(REGISTERED, 1);
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

      while (cursor.moveToNext()) {
        String rawNumber = cursor.getString(0);

        if (rawNumber != null) {
          String e164Number = PhoneNumberFormatter.formatNumber(rawNumber, localNumber);
          results.add(getToken(e164Number));
        }
      }

      return results;
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  private String getToken(String e164number) {
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
