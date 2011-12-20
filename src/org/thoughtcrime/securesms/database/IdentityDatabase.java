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

import java.io.IOException;

import org.thoughtcrime.securesms.crypto.IdentityKey;
import org.thoughtcrime.securesms.crypto.InvalidKeyException;
import org.thoughtcrime.securesms.crypto.MasterCipher;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.util.Base64;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.util.Log;

public class IdentityDatabase extends Database {

  private static final Uri CHANGE_URI = Uri.parse("content://textsecure/identities");
	
  private static final String TABLE_NAME    = "identities";	
  private static final String ID            = "_id";
  public  static final String IDENTITY_KEY  = "key";
  public  static final String IDENTITY_NAME = "name";
  public  static final String MAC           = "mac";
	
  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + ID + " INTEGER PRIMARY KEY, " + 
    IDENTITY_KEY + " TEXT UNIQUE, " + IDENTITY_NAME + " TEXT UNIQUE, "  + 
    MAC + " TEXT);";
	
  public IdentityDatabase(Context context, SQLiteOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }
	
  public Cursor getIdentities() {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    Cursor cursor           = database.query(TABLE_NAME, null, null, null, null, null, null);
		
    if (cursor != null)
      cursor.setNotificationUri(context.getContentResolver(), CHANGE_URI);
		
    return cursor;
  }
	
  public String getNameForIdentity(MasterSecret masterSecret, IdentityKey identityKey) {
    MasterCipher masterCipher = new MasterCipher(masterSecret);
    SQLiteDatabase database   = databaseHelper.getReadableDatabase();
    Cursor cursor             = null;
		
    Log.w("IdentityDatabase", "Querying for: " + Base64.encodeBytes(identityKey.serialize()));
		
    try {
      cursor = database.query(TABLE_NAME, null, IDENTITY_KEY + " = ?", new String[] {Base64.encodeBytes(identityKey.serialize())}, null, null, null);
			
      if (cursor == null || !cursor.moveToFirst())
        return null;
			
      String identityName       = cursor.getString(cursor.getColumnIndexOrThrow(IDENTITY_NAME));
      String identityKeyString  = cursor.getString(cursor.getColumnIndexOrThrow(IDENTITY_KEY));
      byte[] mac                = Base64.decode(cursor.getString(cursor.getColumnIndexOrThrow(MAC)));
			
      if (!masterCipher.verifyMacFor(identityName + identityKeyString, mac)) {
        Log.w("IdentityDatabase", "Mac check failed!");
        return null;
      }
			
      Log.w("IdentityDatabase", "Returning identity name: " + identityName);
      return identityName;
    } catch (IOException e) {
      Log.w("IdentityDatabase", e);
      return null;
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }
	
  public void saveIdentity(MasterSecret masterSecret, IdentityKey identityKey, String tagName) throws InvalidKeyException {
    SQLiteDatabase database   = databaseHelper.getWritableDatabase();
    MasterCipher masterCipher = new MasterCipher(masterSecret);
    String identityKeyString  = Base64.encodeBytes(identityKey.serialize());
    String macString          = Base64.encodeBytes(masterCipher.getMacFor(tagName + identityKeyString));
		
    ContentValues contentValues = new ContentValues();
    contentValues.put(IDENTITY_KEY, identityKeyString);
    contentValues.put(IDENTITY_NAME, tagName);
    contentValues.put(MAC, macString);
		
    long id = database.insert(TABLE_NAME, null, contentValues);
		
    if (id == -1)
      throw new InvalidKeyException("Error inserting key!");
		
    context.getContentResolver().notifyChange(CHANGE_URI, null);
  }

  public void deleteIdentity(String name, String keyString) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    String where            = IDENTITY_NAME + " = ? AND " + IDENTITY_KEY + " = ?";
    String[] args           = new String[] {name, keyString};
		
    database.delete(TABLE_NAME, where, args);
		
    context.getContentResolver().notifyChange(CHANGE_URI, null);
  }

}
