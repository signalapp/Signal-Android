/**
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;

import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.VisibleForTesting;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CanonicalAddressDatabase {

  private static final int    DATABASE_VERSION = 1;
  private static final String DATABASE_NAME    = "canonical_address.db";
  private static final String TABLE            = "canonical_addresses";
  private static final String ID_COLUMN        = "_id";
  private static final String ADDRESS_COLUMN   = "address";

  private static final String DATABASE_CREATE  = "CREATE TABLE " + TABLE + " (" + ID_COLUMN + " integer PRIMARY KEY, " + ADDRESS_COLUMN + " TEXT NOT NULL);";
  private static final String SELECTION_NUMBER = "PHONE_NUMBERS_EQUAL(" + ADDRESS_COLUMN + ", ?)";
  private static final String SELECTION_OTHER  = ADDRESS_COLUMN + " = ? COLLATE NOCASE";
  private static final Object lock             = new Object();

  private static CanonicalAddressDatabase instance;
  private        DatabaseHelper           databaseHelper;

  private final Map<String, Long> addressCache = new ConcurrentHashMap<String, Long>();
  private final Map<Long, String> idCache      = new ConcurrentHashMap<Long, String>();

  public static CanonicalAddressDatabase getInstance(Context context) {
    synchronized (lock) {
      if (instance == null)
        instance = new CanonicalAddressDatabase(context.getApplicationContext());

      return instance;
    }
  }

  private CanonicalAddressDatabase(Context context) {
    databaseHelper = new DatabaseHelper(context, DATABASE_NAME, null, DATABASE_VERSION);
    fillCache();
  }

  public void reset(Context context) {
    DatabaseHelper old  = this.databaseHelper;
    this.databaseHelper = new DatabaseHelper(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    old.close();
    fillCache();
  }

  private void fillCache() {
    Cursor cursor = null;

    try {
      SQLiteDatabase db = databaseHelper.getReadableDatabase();
      cursor            = db.query(TABLE, null, null, null, null, null, null);

      while (cursor != null && cursor.moveToNext()) {
        long   id      = cursor.getLong(cursor.getColumnIndexOrThrow(ID_COLUMN));
        String address = cursor.getString(cursor.getColumnIndexOrThrow(ADDRESS_COLUMN));

        if (address == null || address.trim().length() == 0)
          address = "Anonymous";

        idCache.put(id, address);
        addressCache.put(address, id);
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public String getAddressFromId(long id) {
    String cachedAddress = idCache.get(id);

    if (cachedAddress != null)
      return cachedAddress;

    Cursor cursor = null;

    try {
      Log.w("CanonicalAddressDatabase", "Hitting DB on query [ID].");

      SQLiteDatabase db = databaseHelper.getReadableDatabase();
      cursor            = db.query(TABLE, null, ID_COLUMN + " = ?", new String[] {id+""}, null, null, null);

      if (!cursor.moveToFirst())
        return "Anonymous";

      String address = cursor.getString(cursor.getColumnIndexOrThrow(ADDRESS_COLUMN));

      if (address == null || address.trim().equals("")) {
        return "Anonymous";
      } else {
        idCache.put(id, address);
        return address;
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public void close() {
    databaseHelper.close();
    instance = null;
  }

  public long getCanonicalAddressId(String address) {
    long canonicalAddressId;

    if ((canonicalAddressId = getCanonicalAddressFromCache(address)) != -1)
      return canonicalAddressId;

    canonicalAddressId = getCanonicalAddressIdFromDatabase(address);
    idCache.put(canonicalAddressId, address);
    addressCache.put(address, canonicalAddressId);
    return canonicalAddressId;
  }

  public List<Long> getCanonicalAddressIds(List<String> addresses) {
    List<Long> addressList = new LinkedList<Long>();

    for (String address : addresses) {
      addressList.add(getCanonicalAddressId(address));
    }

    return addressList;
  }

  private long getCanonicalAddressFromCache(String address) {
    Long cachedAddress = addressCache.get(address);
    return cachedAddress == null ? -1L : cachedAddress;
  }

  private long getCanonicalAddressIdFromDatabase(String address) {
    Cursor cursor = null;
    try {
      SQLiteDatabase db           = databaseHelper.getWritableDatabase();
      String[] selectionArguments = new String[] {address};
      boolean isNumber            = isNumberAddress(address);
      cursor                      = db.query(TABLE, null,
                                             isNumber ? SELECTION_NUMBER : SELECTION_OTHER,
                                             selectionArguments, null, null, null);

      if (cursor.getCount() == 0 || !cursor.moveToFirst()) {
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(ADDRESS_COLUMN, address);
        return db.insert(TABLE, ADDRESS_COLUMN, contentValues);
      } else {
        final long   canonicalId = cursor.getLong(cursor.getColumnIndexOrThrow(ID_COLUMN));
        final String oldAddress  = cursor.getString(cursor.getColumnIndexOrThrow(ADDRESS_COLUMN));
        if (oldAddress == null || !oldAddress.equals(address)) {
          ContentValues contentValues = new ContentValues(1);
          contentValues.put(ADDRESS_COLUMN, address);
          db.update(TABLE, contentValues, ID_COLUMN + " = ?", new String[]{canonicalId+""});

          addressCache.remove(oldAddress);
        }
        return canonicalId;
      }
    } finally {
      if (cursor != null) {
        cursor.close();
      }
    }
  }

  @VisibleForTesting
  static boolean isNumberAddress(String number) {
    if (number.contains("@"))
      return false;
    if (GroupUtil.isEncodedGroup(number))
      return false;

    final String networkNumber = PhoneNumberUtils.extractNetworkPortion(number);
    if (TextUtils.isEmpty(networkNumber))
      return false;
    if (networkNumber.length() < 3)
      return false;

    return PhoneNumberUtils.isWellFormedSmsAddress(number);
  }


  private static class DatabaseHelper extends SQLiteOpenHelper {

    public DatabaseHelper(Context context, String name, CursorFactory factory, int version) {
      super(context, name, factory, version);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
      db.execSQL(DATABASE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

  }
}
