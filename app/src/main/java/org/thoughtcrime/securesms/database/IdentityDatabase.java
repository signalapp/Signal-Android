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
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;
import org.thoughtcrime.securesms.database.identity.IdentityRecordList;
import org.thoughtcrime.securesms.database.model.IdentityStoreRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.CursorUtil;
import org.thoughtcrime.securesms.util.IdentityUtil;
import org.thoughtcrime.securesms.util.SqlUtil;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class IdentityDatabase extends Database {

  @SuppressWarnings("unused")
  private static final String TAG = Log.tag(IdentityDatabase.class);

          static final String TABLE_NAME           = "identities";
  private static final String ID                   = "_id";
          static final String ADDRESS              = "address";
          static final String IDENTITY_KEY         = "identity_key";
  private static final String FIRST_USE            = "first_use";
  private static final String TIMESTAMP            = "timestamp";
          static final String VERIFIED             = "verified";
  private static final String NONBLOCKING_APPROVAL = "nonblocking_approval";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + ID                   + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                                                                  ADDRESS              + " INTEGER UNIQUE, " +
                                                                                  IDENTITY_KEY         + " TEXT, " +
                                                                                  FIRST_USE            + " INTEGER DEFAULT 0, " +
                                                                                  TIMESTAMP            + " INTEGER DEFAULT 0, " +
                                                                                  VERIFIED             + " INTEGER DEFAULT 0, " +
                                                                                  NONBLOCKING_APPROVAL + " INTEGER DEFAULT 0);";

  public enum VerifiedStatus {
    DEFAULT, VERIFIED, UNVERIFIED;

    public int toInt() {
      switch (this) {
        case DEFAULT:    return 0;
        case VERIFIED:   return 1;
        case UNVERIFIED: return 2;
        default:         throw new AssertionError();
      }
    }

    public static VerifiedStatus forState(int state) {
      switch (state) {
        case 0:  return DEFAULT;
        case 1:  return VERIFIED;
        case 2:  return UNVERIFIED;
        default: throw new AssertionError("No such state: " + state);
      }
    }
  }

  IdentityDatabase(Context context, SQLCipherOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  public Cursor getIdentities() {
    SQLiteDatabase database = databaseHelper.getSignalReadableDatabase();
    return database.query(TABLE_NAME, null, null, null, null, null, null);
  }

  public @Nullable IdentityReader readerFor(@Nullable Cursor cursor) {
    if (cursor == null) return null;
    return new IdentityReader(cursor);
  }

  public Optional<IdentityRecord> getIdentity(@NonNull String addressName) {
    SQLiteDatabase database = databaseHelper.getSignalReadableDatabase();
    String         query    = ADDRESS + " = ?";
    String[]       args     = SqlUtil.buildArgs(addressName);

    try (Cursor cursor = database.query(TABLE_NAME, null, query, args, null, null, null)) {
      if (cursor.moveToFirst()) {
        return Optional.of(getIdentityRecord(cursor));
      }
    } catch (InvalidKeyException | IOException e) {
      throw new AssertionError(e);
    }

    return Optional.absent();
  }

  public Optional<IdentityRecord> getIdentity(@NonNull RecipientId recipientId) {
    Recipient recipient = Recipient.resolved(recipientId);

    if (recipient.hasServiceIdentifier()) {
      return getIdentity(recipient.requireServiceId());
    } else {
      Log.w(TAG, "Recipient has no service identifier!");
      return Optional.absent();
    }
  }

  public @Nullable IdentityStoreRecord getIdentityStoreRecord(@NonNull String addressName) {
    SQLiteDatabase database   = databaseHelper.getSignalReadableDatabase();
    String         query      = ADDRESS + " = ?";
    String[]       args       = SqlUtil.buildArgs(addressName);

    try (Cursor cursor = database.query(TABLE_NAME, null, query, args, null, null, null)) {
      if (cursor.moveToFirst()) {
        String      serializedIdentity  = CursorUtil.requireString(cursor, IDENTITY_KEY);
        long        timestamp           = CursorUtil.requireLong(cursor, TIMESTAMP);
        int         verifiedStatus      = CursorUtil.requireInt(cursor, VERIFIED);
        boolean     nonblockingApproval = CursorUtil.requireBoolean(cursor, NONBLOCKING_APPROVAL);
        boolean     firstUse            = CursorUtil.requireBoolean(cursor, FIRST_USE);

        return new IdentityStoreRecord(addressName,
                                       new IdentityKey(Base64.decode(serializedIdentity), 0),
                                       VerifiedStatus.forState(verifiedStatus),
                                       firstUse,
                                       timestamp,
                                       nonblockingApproval);
      } else if (addressName.charAt(0) != '+') {
        if (DatabaseFactory.getRecipientDatabase(context).containsPhoneOrUuid(addressName)) {
          Recipient recipient = Recipient.external(context, addressName);

          if (recipient.hasE164()) {
            Log.i(TAG, "Could not find identity for UUID. Attempting E164.");
            return getIdentityStoreRecord(recipient.requireE164());
          } else {
            Log.i(TAG, "Could not find identity for UUID, and our recipient doesn't have an E164.");
          }
        } else {
          Log.i(TAG, "Could not find identity for UUID, and we don't have a recipient.");
        }
      } else {
        Log.i(TAG, "Could not find identity for E164 either.");
      }
    } catch (InvalidKeyException | IOException e) {
      throw new AssertionError(e);
    }

    return null;
  }

  public @NonNull IdentityRecordList getIdentities(@NonNull List<Recipient> recipients) {
    List<String> addressNames = recipients.stream()
                                          .filter(Recipient::hasServiceIdentifier)
                                          .map(Recipient::requireServiceId)
                                          .collect(Collectors.toList());

    if (addressNames.isEmpty()) {
      return IdentityRecordList.EMPTY;
    }

    SQLiteDatabase database = databaseHelper.getSignalReadableDatabase();
    SqlUtil.Query  query    = SqlUtil.buildCollectionQuery(ADDRESS, addressNames);

    List<IdentityRecord> records = new LinkedList<>();

    try (Cursor cursor = database.query(TABLE_NAME, null, query.getWhere(), query.getWhereArgs(), null, null, null)) {
      while (cursor.moveToNext()) {
        try {
          records.add(getIdentityRecord(cursor));
        } catch (InvalidKeyException | IOException e) {
          throw new AssertionError(e);
        }
      }
    }

    return new IdentityRecordList(records);
  }

  public void saveIdentity(@NonNull String addressName,
                           @NonNull RecipientId recipientId,
                           IdentityKey identityKey,
                           VerifiedStatus verifiedStatus,
                           boolean firstUse,
                           long timestamp,
                           boolean nonBlockingApproval)
  {
    saveIdentityInternal(addressName, identityKey, verifiedStatus, firstUse, timestamp, nonBlockingApproval);
    DatabaseFactory.getRecipientDatabase(context).markNeedsSync(recipientId);
  }

  public void saveIdentity(@NonNull RecipientId recipientId,
                           IdentityKey identityKey,
                           VerifiedStatus verifiedStatus,
                           boolean firstUse,
                           long timestamp,
                           boolean nonBlockingApproval)
  {
    saveIdentityInternal(Recipient.resolved(recipientId).requireServiceId(), identityKey, verifiedStatus, firstUse, timestamp, nonBlockingApproval);
    DatabaseFactory.getRecipientDatabase(context).markNeedsSync(recipientId);
  }

  public void setApproval(@NonNull RecipientId recipientId, boolean nonBlockingApproval) {
    SQLiteDatabase database = databaseHelper.getSignalWritableDatabase();

    ContentValues contentValues = new ContentValues(2);
    contentValues.put(NONBLOCKING_APPROVAL, nonBlockingApproval);

    database.update(TABLE_NAME, contentValues, ADDRESS + " = ?", SqlUtil.buildArgs(Recipient.resolved(recipientId).requireServiceId()));

    DatabaseFactory.getRecipientDatabase(context).markNeedsSync(recipientId);
  }

  public void setVerified(@NonNull RecipientId recipientId, IdentityKey identityKey, VerifiedStatus verifiedStatus) {
    SQLiteDatabase database = databaseHelper.getSignalWritableDatabase();

    String   query = ADDRESS + " = ? AND " + IDENTITY_KEY + " = ?";
    String[] args  = SqlUtil.buildArgs(Recipient.resolved(recipientId).requireServiceId(), Base64.encodeBytes(identityKey.serialize()));

    ContentValues contentValues = new ContentValues(1);
    contentValues.put(VERIFIED, verifiedStatus.toInt());

    int updated = database.update(TABLE_NAME, contentValues, query, args);

    if (updated > 0) {
      Optional<IdentityRecord> record = getIdentity(recipientId);
      if (record.isPresent()) EventBus.getDefault().post(record.get());
      DatabaseFactory.getRecipientDatabase(context).markNeedsSync(recipientId);
    }
  }

  public void updateIdentityAfterSync(@NonNull String addressName, IdentityKey identityKey, VerifiedStatus verifiedStatus) {
    boolean hadEntry      = getIdentity(addressName).isPresent();
    boolean keyMatches    = hasMatchingKey(addressName, identityKey);
    boolean statusMatches = keyMatches && hasMatchingStatus(addressName, identityKey, verifiedStatus);

    if (!keyMatches || !statusMatches) {
      saveIdentityInternal(addressName, identityKey, verifiedStatus, !hadEntry, System.currentTimeMillis(), true);

      Optional<IdentityRecord> record = getIdentity(addressName);

      if (record.isPresent()) {
        EventBus.getDefault().post(record.get());
      }
    }

    if (hadEntry && !keyMatches) {
      IdentityUtil.markIdentityUpdate(context, RecipientId.fromExternalPush(addressName));
    }
  }

  private boolean hasMatchingKey(@NonNull String addressName, IdentityKey identityKey) {
    SQLiteDatabase db    = databaseHelper.getSignalReadableDatabase();
    String         query = ADDRESS + " = ? AND " + IDENTITY_KEY + " = ?";
    String[]       args  = SqlUtil.buildArgs(addressName, Base64.encodeBytes(identityKey.serialize()));

    try (Cursor cursor = db.query(TABLE_NAME, null, query, args, null, null, null)) {
      return cursor != null && cursor.moveToFirst();
    }
  }

  private boolean hasMatchingStatus(@NonNull String addressName, IdentityKey identityKey, VerifiedStatus verifiedStatus) {
    SQLiteDatabase db    = databaseHelper.getSignalReadableDatabase();
    String         query = ADDRESS + " = ? AND " + IDENTITY_KEY + " = ? AND " + VERIFIED + " = ?";
    String[]       args  = SqlUtil.buildArgs(addressName, Base64.encodeBytes(identityKey.serialize()), verifiedStatus.toInt());

    try (Cursor cursor = db.query(TABLE_NAME, null, query, args, null, null, null)) {
      return cursor != null && cursor.moveToFirst();
    }
  }

  private static @NonNull IdentityRecord getIdentityRecord(@NonNull Cursor cursor) throws IOException, InvalidKeyException {
    String      addressName         = CursorUtil.requireString(cursor, ADDRESS);
    String      serializedIdentity  = CursorUtil.requireString(cursor, IDENTITY_KEY);
    long        timestamp           = CursorUtil.requireLong(cursor, TIMESTAMP);
    int         verifiedStatus      = CursorUtil.requireInt(cursor, VERIFIED);
    boolean     nonblockingApproval = CursorUtil.requireBoolean(cursor, NONBLOCKING_APPROVAL);
    boolean     firstUse            = CursorUtil.requireBoolean(cursor, FIRST_USE);
    IdentityKey identity            = new IdentityKey(Base64.decode(serializedIdentity), 0);

    return new IdentityRecord(RecipientId.fromExternalPush(addressName), identity, VerifiedStatus.forState(verifiedStatus), firstUse, timestamp, nonblockingApproval);
  }

  private void saveIdentityInternal(@NonNull String addressName,
                                    IdentityKey identityKey,
                                    VerifiedStatus verifiedStatus,
                                    boolean firstUse,
                                    long timestamp,
                                    boolean nonBlockingApproval)
  {
    SQLiteDatabase database          = databaseHelper.getSignalWritableDatabase();
    String         identityKeyString = Base64.encodeBytes(identityKey.serialize());

    ContentValues contentValues = new ContentValues();
    contentValues.put(ADDRESS, addressName);
    contentValues.put(IDENTITY_KEY, identityKeyString);
    contentValues.put(TIMESTAMP, timestamp);
    contentValues.put(VERIFIED, verifiedStatus.toInt());
    contentValues.put(NONBLOCKING_APPROVAL, nonBlockingApproval ? 1 : 0);
    contentValues.put(FIRST_USE, firstUse ? 1 : 0);

    database.replace(TABLE_NAME, null, contentValues);

    EventBus.getDefault().post(new IdentityRecord(RecipientId.fromExternalPush(addressName), identityKey, verifiedStatus, firstUse, timestamp, nonBlockingApproval));
  }

  public static class IdentityRecord {

    private final RecipientId    recipientId;
    private final IdentityKey    identitykey;
    private final VerifiedStatus verifiedStatus;
    private final boolean        firstUse;
    private final long           timestamp;
    private final boolean        nonblockingApproval;

    private IdentityRecord(@NonNull RecipientId recipientId,
                           IdentityKey identitykey,
                           VerifiedStatus verifiedStatus,
                           boolean firstUse,
                           long timestamp,
                           boolean nonblockingApproval)
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

  public static class IdentityReader {
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
