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

import org.thoughtcrime.securesms.util.Base64;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;

public class IdentityDatabase extends Database {

  private static final String TAG = IdentityDatabase.class.getSimpleName();

  private static final Uri CHANGE_URI                  = Uri.parse("content://textsecure/identities");

  private static final String TABLE_NAME           = "identities";
  private static final String ID                   = "_id";
  private static final String RECIPIENT            = "recipient";
  private static final String IDENTITY_KEY         = "key";
  private static final String TIMESTAMP            = "timestamp";
  private static final String FIRST_USE            = "first_use";
  private static final String SEEN                 = "seen";
  private static final String BLOCKING_APPROVAL    = "blocking_approval";
  private static final String NONBLOCKING_APPROVAL = "nonblocking_approval";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME +
      " (" + ID + " INTEGER PRIMARY KEY, " +
      RECIPIENT + " INTEGER UNIQUE, " +
      IDENTITY_KEY + " TEXT, " +
      FIRST_USE + " INTEGER DEFAULT 0, " +
      TIMESTAMP + " INTEGER DEFAULT 0, " +
      SEEN + " INTEGER DEFAULT 0, " +
      BLOCKING_APPROVAL + " INTEGER DEFAULT 0, " +
      NONBLOCKING_APPROVAL + " INTEGER DEFAULT 0);";

  IdentityDatabase(Context context, SQLiteOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  public Optional<IdentityRecord> getIdentity(long recipientId) {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    Cursor         cursor   = null;

    try {
      cursor = database.query(TABLE_NAME, null, RECIPIENT + " = ?",
                              new String[] {recipientId + ""}, null, null, null);

      if (cursor != null && cursor.moveToFirst()) {
        String      serializedIdentity  = cursor.getString(cursor.getColumnIndexOrThrow(IDENTITY_KEY));
        long        timestamp           = cursor.getLong(cursor.getColumnIndexOrThrow(TIMESTAMP));
        long        seen                = cursor.getLong(cursor.getColumnIndexOrThrow(SEEN));
        boolean     blockingApproval    = cursor.getInt(cursor.getColumnIndexOrThrow(BLOCKING_APPROVAL))    == 1;
        boolean     nonblockingApproval = cursor.getInt(cursor.getColumnIndexOrThrow(NONBLOCKING_APPROVAL)) == 1;
        boolean     firstUse            = cursor.getInt(cursor.getColumnIndexOrThrow(FIRST_USE)) == 1;
        IdentityKey identity            = new IdentityKey(Base64.decode(serializedIdentity), 0);

        return Optional.of(new IdentityRecord(identity, firstUse, timestamp, seen, blockingApproval, nonblockingApproval));
      }
    } catch (InvalidKeyException | IOException e) {
      throw new AssertionError(e);
    } finally {
      if (cursor != null) cursor.close();
    }

    return Optional.absent();
  }

  public void saveIdentity(long recipientId, IdentityKey identityKey, boolean firstUse,
                           long timestamp, boolean blockingApproval, boolean nonBlockingApproval)
  {
    SQLiteDatabase database          = databaseHelper.getWritableDatabase();
    String         identityKeyString = Base64.encodeBytes(identityKey.serialize());

    ContentValues contentValues = new ContentValues();
    contentValues.put(RECIPIENT, recipientId);
    contentValues.put(IDENTITY_KEY, identityKeyString);
    contentValues.put(TIMESTAMP, timestamp);
    contentValues.put(BLOCKING_APPROVAL, blockingApproval ? 1 : 0);
    contentValues.put(NONBLOCKING_APPROVAL, nonBlockingApproval ? 1 : 0);
    contentValues.put(FIRST_USE, firstUse ? 1 : 0);
    contentValues.put(SEEN, 0);

    database.replace(TABLE_NAME, null, contentValues);

    context.getContentResolver().notifyChange(CHANGE_URI, null);
  }

  public void setApproval(long recipientId, boolean blockingApproval, boolean nonBlockingApproval) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();

    ContentValues contentValues = new ContentValues(2);
    contentValues.put(BLOCKING_APPROVAL, blockingApproval);
    contentValues.put(NONBLOCKING_APPROVAL, nonBlockingApproval);

    database.update(TABLE_NAME, contentValues, RECIPIENT + " = ?",
                    new String[] {String.valueOf(recipientId)});

    context.getContentResolver().notifyChange(CHANGE_URI, null);
  }

  public void setSeen(long recipientId) {
    Log.w(TAG, "Setting seen to current time: " + recipientId);
    SQLiteDatabase database = databaseHelper.getWritableDatabase();

    ContentValues contentValues = new ContentValues(1);
    contentValues.put(SEEN, System.currentTimeMillis());

    database.update(TABLE_NAME, contentValues, RECIPIENT + " = ? AND " + SEEN + " = 0",
                    new String[] {String.valueOf(recipientId)});
  }

  public static class IdentityRecord {

    private final IdentityKey identitykey;
    private final boolean     firstUse;
    private final long        timestamp;
    private final long        seen;
    private final boolean     blockingApproval;
    private final boolean     nonblockingApproval;

    private IdentityRecord(IdentityKey identitykey, boolean firstUse, long timestamp,
                           long seen, boolean blockingApproval, boolean nonblockingApproval)
    {
      this.identitykey         = identitykey;
      this.firstUse            = firstUse;
      this.timestamp           = timestamp;
      this.seen                = seen;
      this.blockingApproval    = blockingApproval;
      this.nonblockingApproval = nonblockingApproval;
    }

    public IdentityKey getIdentityKey() {
      return identitykey;
    }

    public long getTimestamp() {
      return timestamp;
    }

    public long getSeen() {
      return seen;
    }

    public boolean isApprovedBlocking() {
      return blockingApproval;
    }

    public boolean isApprovedNonBlocking() {
      return nonblockingApproval;
    }

    public boolean isFirstUse() {
      return firstUse;
    }

  }

}
