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
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.util.Log;

import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.Base64;
import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.InvalidKeyException;
import org.thoughtcrime.securesms.crypto.MasterCipher;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.whispersystems.libaxolotl.InvalidMessageException;

import java.io.IOException;

import de.gdata.messaging.util.ProfileAccessor;

public class IdentityDatabase extends Database {

  private static final Uri CHANGE_URI = Uri.parse("content://textsecure/identities");

  private static final String TABLE_NAME    = "identities";
  private static final String ID            = "_id";
  public  static final String RECIPIENT     = "recipient";
  public  static final String IDENTITY_KEY  = "key";
  public  static final String MAC           = "mac";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME +
      " (" + ID + " INTEGER PRIMARY KEY, " +
      RECIPIENT + " INTEGER UNIQUE, " +
      IDENTITY_KEY + " TEXT, " +
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

  public boolean isValidIdentity(MasterSecret masterSecret,
                                 long recipientId,
                                 IdentityKey theirIdentity)
  {
    SQLiteDatabase database   = databaseHelper.getReadableDatabase();
    MasterCipher masterCipher = new MasterCipher(masterSecret);
    Cursor cursor             = null;

    try {
      cursor = database.query(TABLE_NAME, null, RECIPIENT + " = ?",
                              new String[] {recipientId+""}, null, null,null);

      if (cursor != null && cursor.moveToFirst()) {
        String serializedIdentity = cursor.getString(cursor.getColumnIndexOrThrow(IDENTITY_KEY));
        String mac                = cursor.getString(cursor.getColumnIndexOrThrow(MAC));

        if (!masterCipher.verifyMacFor(recipientId + serializedIdentity, Base64.decode(mac))) {
          Log.w("IdentityDatabase", "MAC failed");
          return false;
        }

        IdentityKey ourIdentity = new IdentityKey(Base64.decode(serializedIdentity), 0);

        return ourIdentity.equals(theirIdentity);
      } else {
        return true;
      }
    } catch (IOException e) {
      Log.w("IdentityDatabase", e);
      return false;
    } catch (InvalidKeyException e) {
      Log.w("IdentityDatabase", e);
      return false;
    } finally {
      if (cursor != null) {
        cursor.close();
      }
    }
  }

  public void saveIdentity(MasterSecret masterSecret, long recipientId, IdentityKey identityKey)
  {
    SQLiteDatabase database   = databaseHelper.getWritableDatabase();
    MasterCipher masterCipher = new MasterCipher(masterSecret);
    String identityKeyString  = Base64.encodeBytes(identityKey.serialize());
    String macString          = Base64.encodeBytes(masterCipher.getMacFor(recipientId +
                                                                              identityKeyString));

    ContentValues contentValues = new ContentValues();
    contentValues.put(RECIPIENT, recipientId);
    contentValues.put(IDENTITY_KEY, identityKeyString);
    contentValues.put(MAC, macString);

    database.replace(TABLE_NAME, null, contentValues);

    context.getContentResolver().notifyChange(CHANGE_URI, null);
    try {
      long[] ids = new long[1];
      ids[0] = recipientId;
      ProfileAccessor.sendProfileUpdate(context, masterSecret, RecipientFactory.getRecipientsForIds(context, ids, false));
    } catch (InvalidMessageException e) {
      Log.w("GDATA", e);
    }
  }

  public void deleteIdentity(long id) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    database.delete(TABLE_NAME, ID_WHERE, new String[] {id+""});

    context.getContentResolver().notifyChange(CHANGE_URI, null);
  }

  public Reader readerFor(MasterSecret masterSecret, Cursor cursor) {
    return new Reader(masterSecret, cursor);
  }

  public class Reader {
    private final Cursor cursor;
    private final MasterCipher cipher;

    public Reader(MasterSecret masterSecret, Cursor cursor) {
      this.cursor = cursor;
      this.cipher = new MasterCipher(masterSecret);
    }

    public Identity getCurrent() {
      long       recipientId = cursor.getLong(cursor.getColumnIndexOrThrow(RECIPIENT));
      Recipients recipients  = RecipientFactory.getRecipientsForIds(context, new long[]{recipientId}, true);

      try {
        String identityKeyString = cursor.getString(cursor.getColumnIndexOrThrow(IDENTITY_KEY));
        String mac               = cursor.getString(cursor.getColumnIndexOrThrow(MAC));

        if (!cipher.verifyMacFor(recipientId + identityKeyString, Base64.decode(mac))) {
          return new Identity(recipients, null);
        }

        IdentityKey identityKey = new IdentityKey(Base64.decode(identityKeyString), 0);
        return new Identity(recipients, identityKey);
      } catch (IOException e) {
        Log.w("IdentityDatabase", e);
        return new Identity(recipients, null);
      } catch (InvalidKeyException e) {
        Log.w("IdentityDatabase", e);
        return new Identity(recipients, null);
      }
    }
  }

  public class Identity {
    private final Recipients  recipients;
    private final IdentityKey identityKey;

    public Identity(Recipients recipients, IdentityKey identityKey) {
      this.recipients  = recipients;
      this.identityKey = identityKey;
    }

    public Recipients getRecipients() {
      return recipients;
    }

    public IdentityKey getIdentityKey() {
      return identityKey;
    }
  }

}
