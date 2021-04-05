/*
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.greenrobot.eventbus.EventBus;
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;
import org.thoughtcrime.securesms.database.identity.IdentityRecordList;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.IdentityUtil;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class IdentityDatabase extends Database {

  @SuppressWarnings("unused")
  private static final String TAG = IdentityDatabase.class.getSimpleName();

          static final String TABLE_NAME           = "identities";
  private static final String ID                   = "_id";
          static final String RECIPIENT_ID         = "address";
          static final String IDENTITY_KEY         = "key";
  private static final String TIMESTAMP            = "timestamp";
  private static final String FIRST_USE            = "first_use";
  private static final String NONBLOCKING_APPROVAL = "nonblocking_approval";
          static final String VERIFIED             = "verified";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME +
      " (" + ID + " INTEGER PRIMARY KEY, " +
      RECIPIENT_ID + " INTEGER UNIQUE, " +
      IDENTITY_KEY + " TEXT, " +
      FIRST_USE + " INTEGER DEFAULT 0, " +
      TIMESTAMP + " INTEGER DEFAULT 0, " +
      VERIFIED + " INTEGER DEFAULT 0, " +
      NONBLOCKING_APPROVAL + " INTEGER DEFAULT 0);";

  public enum VerifiedStatus {
    DEFAULT, VERIFIED, UNVERIFIED;

    public int toInt() {
      if      (this == DEFAULT)    return 0;
      else if (this == VERIFIED)   return 1;
      else if (this == UNVERIFIED) return 2;
      else throw new AssertionError();
    }

    public static VerifiedStatus forState(int state) {
      if      (state == 0) return DEFAULT;
      else if (state == 1) return VERIFIED;
      else if (state == 2) return UNVERIFIED;
      else throw new AssertionError("No such state: " + state);
    }
  }

  IdentityDatabase(Context context, SQLCipherOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  public Cursor getIdentities() {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    return database.query(TABLE_NAME, null, null, null, null, null, null);
  }

  public @Nullable IdentityReader readerFor(@Nullable Cursor cursor) {
    if (cursor == null) return null;
    return new IdentityReader(cursor);
  }

  public Optional<IdentityRecord> getIdentity(@NonNull RecipientId recipientId) {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    Cursor         cursor   = null;

    try {
      cursor = database.query(TABLE_NAME, null, RECIPIENT_ID + " = ?",
                              new String[] {recipientId.serialize()}, null, null, null);

      if (cursor != null && cursor.moveToFirst()) {
        return Optional.of(getIdentityRecord(cursor));
      }
    } catch (InvalidKeyException | IOException e) {
      throw new AssertionError(e);
    } finally {
      if (cursor != null) cursor.close();
    }

    return Optional.absent();
  }

  public @NonNull IdentityRecordList getIdentities(@NonNull List<Recipient> recipients) {
    List<IdentityRecord> records       = new LinkedList<>();
    SQLiteDatabase       database      = databaseHelper.getReadableDatabase();
    String[]             selectionArgs = new String[1];

    database.beginTransaction();
    try {
      for (Recipient recipient : recipients) {
        selectionArgs[0] = recipient.getId().serialize();

        try (Cursor cursor = database.query(TABLE_NAME, null, RECIPIENT_ID + " = ?", selectionArgs, null, null, null)) {
          if (cursor.moveToFirst()) {
            records.add(getIdentityRecord(cursor));
          }
        } catch (InvalidKeyException | IOException e) {
          throw new AssertionError(e);
        }
      }
    } finally {
      database.endTransaction();
    }

    return new IdentityRecordList(records);
  }

  public void saveIdentity(@NonNull RecipientId recipientId, IdentityKey identityKey, VerifiedStatus verifiedStatus,
                           boolean firstUse, long timestamp, boolean nonBlockingApproval)
  {
    saveIdentityInternal(recipientId, identityKey, verifiedStatus, firstUse, timestamp, nonBlockingApproval);
    DatabaseFactory.getRecipientDatabase(context).markDirty(recipientId, RecipientDatabase.DirtyState.UPDATE);
  }

  public void setApproval(@NonNull RecipientId recipientId, boolean nonBlockingApproval) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();

    ContentValues contentValues = new ContentValues(2);
    contentValues.put(NONBLOCKING_APPROVAL, nonBlockingApproval);

    database.update(TABLE_NAME, contentValues, RECIPIENT_ID + " = ?", new String[] {recipientId.serialize()});

    DatabaseFactory.getRecipientDatabase(context).markDirty(recipientId, RecipientDatabase.DirtyState.UPDATE);
  }

  public void setVerified(@NonNull RecipientId recipientId, IdentityKey identityKey, VerifiedStatus verifiedStatus) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();

    ContentValues contentValues = new ContentValues(1);
    contentValues.put(VERIFIED, verifiedStatus.toInt());

    int updated = database.update(TABLE_NAME, contentValues, RECIPIENT_ID + " = ? AND " + IDENTITY_KEY + " = ?",
                                  new String[] {recipientId.serialize(), Base64.encodeBytes(identityKey.serialize())});

    if (updated > 0) {
      Optional<IdentityRecord> record = getIdentity(recipientId);
      if (record.isPresent()) EventBus.getDefault().post(record.get());
      DatabaseFactory.getRecipientDatabase(context).markDirty(recipientId, RecipientDatabase.DirtyState.UPDATE);
    }
  }

  public void updateIdentityAfterSync(@NonNull RecipientId id, IdentityKey identityKey, VerifiedStatus verifiedStatus) {
    boolean hadEntry      = getIdentity(id).isPresent();
    boolean keyMatches    = hasMatchingKey(id, identityKey);
    boolean statusMatches = keyMatches && hasMatchingStatus(id, identityKey, verifiedStatus);

    if (!keyMatches || !statusMatches) {
      saveIdentityInternal(id, identityKey, verifiedStatus, !hadEntry, System.currentTimeMillis(), true);
      Optional<IdentityRecord> record = getIdentity(id);
      if (record.isPresent()) EventBus.getDefault().post(record.get());
    }

    if (hadEntry && !keyMatches) {
      IdentityUtil.markIdentityUpdate(context, id);
    }
  }

  private boolean hasMatchingKey(@NonNull RecipientId id, IdentityKey identityKey) {
    SQLiteDatabase db    = databaseHelper.getReadableDatabase();
    String         query = RECIPIENT_ID + " = ? AND " + IDENTITY_KEY + " = ?";
    String[]       args  = new String[]{id.serialize(), Base64.encodeBytes(identityKey.serialize())};

    try (Cursor cursor = db.query(TABLE_NAME, null, query, args, null, null, null)) {
      return cursor != null && cursor.moveToFirst();
    }
  }

  private boolean hasMatchingStatus(@NonNull RecipientId id, IdentityKey identityKey, VerifiedStatus verifiedStatus) {
    SQLiteDatabase db    = databaseHelper.getReadableDatabase();
    String         query = RECIPIENT_ID + " = ? AND " + IDENTITY_KEY + " = ? AND " + VERIFIED + " = ?";
    String[]       args  = new String[]{id.serialize(), Base64.encodeBytes(identityKey.serialize()), String.valueOf(verifiedStatus.toInt())};

    try (Cursor cursor = db.query(TABLE_NAME, null, query, args, null, null, null)) {
      return cursor != null && cursor.moveToFirst();
    }
  }

  private static @NonNull IdentityRecord getIdentityRecord(@NonNull Cursor cursor) throws IOException, InvalidKeyException {
    long        recipientId         = cursor.getLong(cursor.getColumnIndexOrThrow(RECIPIENT_ID));
    String      serializedIdentity  = cursor.getString(cursor.getColumnIndexOrThrow(IDENTITY_KEY));
    long        timestamp           = cursor.getLong(cursor.getColumnIndexOrThrow(TIMESTAMP));
    int         verifiedStatus      = cursor.getInt(cursor.getColumnIndexOrThrow(VERIFIED));
    boolean     nonblockingApproval = cursor.getInt(cursor.getColumnIndexOrThrow(NONBLOCKING_APPROVAL)) == 1;
    boolean     firstUse            = cursor.getInt(cursor.getColumnIndexOrThrow(FIRST_USE))            == 1;
    IdentityKey identity            = new IdentityKey(Base64.decode(serializedIdentity), 0);

    return new IdentityRecord(RecipientId.from(recipientId), identity, VerifiedStatus.forState(verifiedStatus), firstUse, timestamp, nonblockingApproval);
  }

  private void saveIdentityInternal(@NonNull RecipientId recipientId, IdentityKey identityKey, VerifiedStatus verifiedStatus,
                                    boolean firstUse, long timestamp, boolean nonBlockingApproval)
  {
    SQLiteDatabase database          = databaseHelper.getWritableDatabase();
    String         identityKeyString = Base64.encodeBytes(identityKey.serialize());

    ContentValues contentValues = new ContentValues();
    contentValues.put(RECIPIENT_ID, recipientId.serialize());
    contentValues.put(IDENTITY_KEY, identityKeyString);
    contentValues.put(TIMESTAMP, timestamp);
    contentValues.put(VERIFIED, verifiedStatus.toInt());
    contentValues.put(NONBLOCKING_APPROVAL, nonBlockingApproval ? 1 : 0);
    contentValues.put(FIRST_USE, firstUse ? 1 : 0);

    database.replace(TABLE_NAME, null, contentValues);

    EventBus.getDefault().post(new IdentityRecord(recipientId, identityKey, verifiedStatus,
        firstUse, timestamp, nonBlockingApproval));
  }

  public static class IdentityRecord {

    private final RecipientId    recipientId;
    private final IdentityKey    identitykey;
    private final VerifiedStatus verifiedStatus;
    private final boolean        firstUse;
    private final long           timestamp;
    private final boolean        nonblockingApproval;

    private IdentityRecord(@NonNull RecipientId recipientId,
                           IdentityKey identitykey, VerifiedStatus verifiedStatus,
                           boolean firstUse, long timestamp, boolean nonblockingApproval)
    {
      this.recipientId         = recipientId;
      this.identitykey         = identitykey;
      this.verifiedStatus      = verifiedStatus;
      this.firstUse            = firstUse;
      this.timestamp           = timestamp;
      this.nonblockingApproval = nonblockingApproval;
    }

    public RecipientId getRecipientId() {
      return recipientId;
    }

    public IdentityKey getIdentityKey() {
      return identitykey;
    }

    public long getTimestamp() {
      return timestamp;
    }

    public VerifiedStatus getVerifiedStatus() {
      return verifiedStatus;
    }

    public boolean isApprovedNonBlocking() {
      return nonblockingApproval;
    }

    public boolean isFirstUse() {
      return firstUse;
    }

    @Override
    public @NonNull String toString() {
      return "{recipientId: " + recipientId + ", identityKey: " + identitykey + ", verifiedStatus: " + verifiedStatus + ", firstUse: " + firstUse + "}";
    }

  }

  public class IdentityReader {
    private final Cursor cursor;

    IdentityReader(@NonNull Cursor cursor) {
      this.cursor = cursor;
    }

    public @Nullable IdentityRecord getNext() {
      if (cursor.moveToNext()) {
        try {
          return getIdentityRecord(cursor);
        } catch (IOException | InvalidKeyException e) {
          throw new AssertionError(e);
        }
      }

      return null;
    }

    public void close() {
      cursor.close();
    }
  }

}
