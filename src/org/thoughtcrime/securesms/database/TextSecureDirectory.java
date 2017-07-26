package org.thoughtcrime.securesms.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.support.annotation.NonNull;
import android.util.Log;

import org.whispersystems.signalservice.api.push.ContactTokenDetails;
import org.whispersystems.signalservice.api.util.InvalidNumberException;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TextSecureDirectory {

  private static final int INTRODUCED_CHANGE_FROM_TOKEN_TO_E164_NUMBER = 2;
  private static final int INTRODUCED_VOICE_COLUMN                     = 4;
  private static final int INTRODUCED_VIDEO_COLUMN                     = 5;

  private static final String DATABASE_NAME    = "whisper_directory.db";
  private static final int    DATABASE_VERSION = 5;

  private static final String TABLE_NAME   = "directory";
  private static final String ID           = "_id";
  private static final String NUMBER       = "number";
  private static final String REGISTERED   = "registered";
  private static final String RELAY        = "relay";
  private static final String TIMESTAMP    = "timestamp";
  private static final String VOICE        = "voice";
  private static final String VIDEO        = "video";

  private static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + "(" + ID + " INTEGER PRIMARY KEY, " +
                              NUMBER       + " TEXT UNIQUE, " +
                              REGISTERED   + " INTEGER, " +
                              RELAY        + " TEXT, " +
                              TIMESTAMP    + " INTEGER, " +
                              VOICE        + " INTEGER, " +
                              VIDEO        + " INTEGER);";

  private static final Object instanceLock = new Object();
  private static volatile TextSecureDirectory instance;

  public static TextSecureDirectory getInstance(Context context) {
    if (instance == null) {
      synchronized (instanceLock) {
        if (instance == null) {
          instance = new TextSecureDirectory(context.getApplicationContext());
        }
      }
    }

    return instance;
  }

  private final DatabaseHelper databaseHelper;
  private final Context        context;

  private TextSecureDirectory(Context context) {
    this.context = context;
    this.databaseHelper = new DatabaseHelper(context, DATABASE_NAME, null, DATABASE_VERSION);
  }

  public boolean isSecureTextSupported(@NonNull Address address) throws NotInDirectoryException {
    if (address.isEmail()) return false;
    if (address.isGroup()) return true;

    SQLiteDatabase db = databaseHelper.getReadableDatabase();
    Cursor cursor = null;

    try {
      cursor = db.query(TABLE_NAME,
          new String[]{REGISTERED}, NUMBER + " = ?",
          new String[] {address.serialize()}, null, null, null);

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

//  public boolean isSecureVoiceSupported(String e164number) throws NotInDirectoryException {
//    if (TextUtils.isEmpty(e164number)) {
//      return false;
//    }
//
//    SQLiteDatabase db     = databaseHelper.getReadableDatabase();
//    Cursor         cursor = null;
//
//    try {
//      cursor = db.query(TABLE_NAME,
//                        new String[]{VOICE}, NUMBER + " = ?",
//                        new String[] {e164number}, null, null, null);
//
//      if (cursor != null && cursor.moveToFirst()) {
//        return cursor.getInt(0) == 1;
//      } else {
//        throw new NotInDirectoryException();
//      }
//
//    } finally {
//      if (cursor != null)
//        cursor.close();
//    }
//  }

//  public boolean isSecureVideoSupported(String e164number) throws NotInDirectoryException {
//    if (TextUtils.isEmpty(e164number)) {
//      return false;
//    }
//
//    SQLiteDatabase db     = databaseHelper.getReadableDatabase();
//    Cursor         cursor = null;
//
//    try {
//      cursor = db.query(TABLE_NAME,
//                        new String[]{VIDEO}, NUMBER + " = ?",
//                        new String[] {e164number}, null, null, null);
//
//      if (cursor != null && cursor.moveToFirst()) {
//        return cursor.getInt(0) == 1;
//      } else {
//        throw new NotInDirectoryException();
//      }
//
//    } finally {
//      if (cursor != null)
//        cursor.close();
//    }
//  }

  public String getRelay(String e164number) {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    Cursor         cursor   = null;

    try {
      cursor = database.query(TABLE_NAME, null, NUMBER + " = ?", new String[]{e164number}, null, null, null);

      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getString(cursor.getColumnIndexOrThrow(RELAY));
      }

      return null;
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public void setNumber(ContactTokenDetails token, boolean active) {
    SQLiteDatabase db     = databaseHelper.getWritableDatabase();
    ContentValues  values = new ContentValues();
    values.put(NUMBER, token.getNumber());
    values.put(REGISTERED, active ? 1 : 0);
    values.put(TIMESTAMP, System.currentTimeMillis());
    values.put(RELAY, token.getRelay());
    values.put(VOICE, token.isVoice());
    values.put(VIDEO, token.isVideo());
    db.replace(TABLE_NAME, null, values);
  }

  public void setNumbers(List<ContactTokenDetails> activeTokens, Collection<String> inactiveTokens) {
    long timestamp    = System.currentTimeMillis();
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.beginTransaction();

    try {
      for (ContactTokenDetails token : activeTokens) {
        Log.w("Directory", "Adding active token: " + token.getNumber() + ", " + token.getToken() + ", video: " + token.isVideo());
        ContentValues values = new ContentValues();
        values.put(NUMBER, token.getNumber());
        values.put(REGISTERED, 1);
        values.put(TIMESTAMP, timestamp);
        values.put(RELAY, token.getRelay());
        values.put(VOICE, token.isVoice());
        values.put(VIDEO, token.isVideo());
        db.replace(TABLE_NAME, null, values);
      }

      for (String token : inactiveTokens) {
        ContentValues values = new ContentValues();
        values.put(NUMBER, token);
        values.put(REGISTERED, 0);
        values.put(TIMESTAMP, timestamp);
        db.replace(TABLE_NAME, null, values);
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public Set<String> getPushEligibleContactNumbers(String localNumber) {
    final Uri         uri     = Phone.CONTENT_URI;
    final Set<String> results = new HashSet<>();
          Cursor      cursor  = null;

    try {
      cursor = context.getContentResolver().query(uri, new String[] {Phone.NUMBER}, null, null, null);

      while (cursor != null && cursor.moveToNext()) {
        final String rawNumber = cursor.getString(0);
        if (rawNumber != null) {
          try {
            final String e164Number = PhoneNumberFormatter.formatNumber(rawNumber, localNumber);
            results.add(e164Number);
          } catch (InvalidNumberException e) {
            Log.w("Directory", "Invalid number: " + rawNumber);
          }
        }
      }

      if (cursor != null)
        cursor.close();

      final SQLiteDatabase readableDb = databaseHelper.getReadableDatabase();
      if (readableDb != null) {
        cursor = readableDb.query(TABLE_NAME, new String[]{NUMBER},
            null, null, null, null, null);

        while (cursor != null && cursor.moveToNext()) {
          results.add(cursor.getString(0));
        }
      }

      return results;
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public List<String> getActiveNumbers() {
    final List<String> results = new ArrayList<>();
    Cursor cursor = null;
    try {
      cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME, new String[]{NUMBER},
          REGISTERED + " = 1", null, null, null, null);

      while (cursor != null && cursor.moveToNext()) {
        results.add(cursor.getString(0));
      }
      return results;
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  private static class DatabaseHelper extends SQLiteOpenHelper {

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
      if (oldVersion < INTRODUCED_CHANGE_FROM_TOKEN_TO_E164_NUMBER) {
        db.execSQL("DROP TABLE directory;");
        db.execSQL("CREATE TABLE directory ( _id INTEGER PRIMARY KEY, " +
                   "number TEXT UNIQUE, " +
                   "registered INTEGER, " +
                   "relay TEXT, " +
                   "supports_sms INTEGER, " +
                   "timestamp INTEGER);");
      }

      if (oldVersion < INTRODUCED_VOICE_COLUMN) {
        db.execSQL("ALTER TABLE directory ADD COLUMN voice INTEGER;");
      }

      if (oldVersion < INTRODUCED_VIDEO_COLUMN) {
        db.execSQL("ALTER TABLE directory ADD COLUMN video INTEGER;");
      }
    }
  }

}
