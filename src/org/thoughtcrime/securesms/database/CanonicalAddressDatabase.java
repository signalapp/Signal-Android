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

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteDatabase.CursorFactory;

public class CanonicalAddressDatabase {

  private static final int    DATABASE_VERSION = 1;
  private static final String DATABASE_NAME    = "canonical_address.db";
  private static final String TABLE            = "canonical_addresses";
  private static final String ID_COLUMN        = "_id";
  private static final String ADDRESS_COLUMN   = "address";
	
  private static final String DATABASE_CREATE  = "CREATE TABLE " + TABLE + " (" + ID_COLUMN + " integer PRIMARY KEY, " + ADDRESS_COLUMN + " TEXT NOT NULL);";
  private static final String[] ID_PROJECTION  = {ID_COLUMN};
  private static final String SELECTION        = "PHONE_NUMBERS_EQUAL(" + ADDRESS_COLUMN + ", ?)";	
  private static final Object lock             = new Object();
	
  private static CanonicalAddressDatabase instance;
  private final DatabaseHelper databaseHelper;
  private final HashMap<String,Long> addressCache = new HashMap<String,Long>();
  private final Map<String,String> idCache        = Collections.synchronizedMap(new HashMap<String,String>());
	
  public static CanonicalAddressDatabase getInstance(Context context) {
    synchronized (lock) {
      if (instance == null)
	instance = new CanonicalAddressDatabase(context);
			
      return instance;
    }
  }

  private CanonicalAddressDatabase(Context context) {
    databaseHelper = new DatabaseHelper(context, DATABASE_NAME, null, DATABASE_VERSION);
  }
	
  public String getAddressFromId(String id) {
    if (id == null || id.trim().equals("")) return "Anonymous";

    String cachedAddress = idCache.get(id);		
    if (cachedAddress != null)
      return cachedAddress;
		
    Cursor cursor = null;
		
    try {
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
	
  public long getCanonicalAddress(String address) {
    long canonicalAddress;
		
    if ((canonicalAddress = getCanonicalAddressFromCache(address)) != -1)
      return canonicalAddress;
		
    canonicalAddress = getCanonicalAddressFromDatabase(address);
    addressCache.put(address, canonicalAddress);
		
    return canonicalAddress;
  }
	
  public List<Long> getCanonicalAddresses(List<String> addresses) {
    List<Long> addressList = new LinkedList<Long>();
		
    for (String address : addresses) {
      addressList.add(getCanonicalAddress(address));
    }
		
    return addressList;
  }
	
  private long getCanonicalAddressFromCache(String address) {
    if (addressCache.containsKey(address))
      return new Long(addressCache.get(address));
		
    return -1L;
  }
	
  private long getCanonicalAddressFromDatabase(String address) {
    Cursor cursor = null;
    try {
      SQLiteDatabase db           = databaseHelper.getReadableDatabase();
      String[] selectionArguments = new String[] {address};
      cursor                      = db.query(TABLE, ID_PROJECTION, SELECTION, selectionArguments, null, null, null);

      if (cursor.getCount() == 0 || !cursor.moveToFirst()) {
	ContentValues contentValues = new ContentValues(1);
	contentValues.put(ADDRESS_COLUMN, address);

	return db.insert(TABLE, ADDRESS_COLUMN, contentValues);
      }

      return cursor.getLong(cursor.getColumnIndexOrThrow(ID_COLUMN));
    } finally {
      if (cursor != null) {
	cursor.close();
      }
    }       
        
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
