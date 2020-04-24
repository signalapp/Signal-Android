package org.thoughtcrime.securesms.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;
import com.google.android.gms.common.util.ArrayUtils;

import net.sqlcipher.database.SQLiteDatabase;

import org.signal.zkgroup.profiles.ProfileKey;
import org.signal.zkgroup.profiles.ProfileKeyCredential;
import org.thoughtcrime.securesms.color.MaterialColor;
import org.thoughtcrime.securesms.database.IdentityDatabase.IdentityRecord;
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.profiles.ProfileName;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.storage.StorageSyncHelper;
import org.thoughtcrime.securesms.storage.StorageSyncHelper.RecordUpdate;
import org.thoughtcrime.securesms.storage.StorageSyncModels;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.IdentityUtil;
import org.thoughtcrime.securesms.util.SqlUtil;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.storage.SignalAccountRecord;
import org.whispersystems.signalservice.api.storage.SignalContactRecord;
import org.whispersystems.signalservice.api.storage.SignalGroupV1Record;
import org.whispersystems.signalservice.api.storage.StorageId;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class RecipientDatabase extends Database {

  private static final String TAG = RecipientDatabase.class.getSimpleName();

          static final String TABLE_NAME               = "recipient";
  public  static final String ID                       = "_id";
  private static final String UUID                     = "uuid";
  private static final String USERNAME                 = "username";
  public  static final String PHONE                    = "phone";
  public  static final String EMAIL                    = "email";
          static final String GROUP_ID                 = "group_id";
  private static final String GROUP_TYPE               = "group_type";
  private static final String BLOCKED                  = "blocked";
  private static final String MESSAGE_RINGTONE         = "message_ringtone";
  private static final String MESSAGE_VIBRATE          = "message_vibrate";
  private static final String CALL_RINGTONE            = "call_ringtone";
  private static final String CALL_VIBRATE             = "call_vibrate";
  private static final String NOTIFICATION_CHANNEL     = "notification_channel";
  private static final String MUTE_UNTIL               = "mute_until";
  private static final String COLOR                    = "color";
  private static final String SEEN_INVITE_REMINDER     = "seen_invite_reminder";
  private static final String DEFAULT_SUBSCRIPTION_ID  = "default_subscription_id";
  private static final String MESSAGE_EXPIRATION_TIME  = "message_expiration_time";
  public  static final String REGISTERED               = "registered";
  public  static final String SYSTEM_DISPLAY_NAME      = "system_display_name";
  private static final String SYSTEM_PHOTO_URI         = "system_photo_uri";
  public  static final String SYSTEM_PHONE_TYPE        = "system_phone_type";
  public  static final String SYSTEM_PHONE_LABEL       = "system_phone_label";
  private static final String SYSTEM_CONTACT_URI       = "system_contact_uri";
  private static final String SYSTEM_INFO_PENDING      = "system_info_pending";
  private static final String PROFILE_KEY              = "profile_key";
  private static final String PROFILE_KEY_CREDENTIAL   = "profile_key_credential";
  private static final String SIGNAL_PROFILE_AVATAR    = "signal_profile_avatar";
  private static final String PROFILE_SHARING          = "profile_sharing";
  private static final String UNIDENTIFIED_ACCESS_MODE = "unidentified_access_mode";
  private static final String FORCE_SMS_SELECTION      = "force_sms_selection";
  private static final String UUID_CAPABILITY          = "uuid_supported";
  private static final String GROUPS_V2_CAPABILITY     = "gv2_capability";
  private static final String STORAGE_SERVICE_ID       = "storage_service_key";
  private static final String DIRTY                    = "dirty";
  private static final String PROFILE_GIVEN_NAME       = "signal_profile_name";
  private static final String PROFILE_FAMILY_NAME      = "profile_family_name";
  private static final String PROFILE_JOINED_NAME      = "profile_joined_name";

  public  static final String SEARCH_PROFILE_NAME      = "search_signal_profile";
  private static final String SORT_NAME                = "sort_name";
  private static final String IDENTITY_STATUS          = "identity_status";
  private static final String IDENTITY_KEY             = "identity_key";


  private static final String[] RECIPIENT_PROJECTION = new String[] {
      UUID, USERNAME, PHONE, EMAIL, GROUP_ID, GROUP_TYPE,
      BLOCKED, MESSAGE_RINGTONE, CALL_RINGTONE, MESSAGE_VIBRATE, CALL_VIBRATE, MUTE_UNTIL, COLOR, SEEN_INVITE_REMINDER, DEFAULT_SUBSCRIPTION_ID, MESSAGE_EXPIRATION_TIME, REGISTERED,
      PROFILE_KEY, PROFILE_KEY_CREDENTIAL,
      SYSTEM_DISPLAY_NAME, SYSTEM_PHOTO_URI, SYSTEM_PHONE_LABEL, SYSTEM_PHONE_TYPE, SYSTEM_CONTACT_URI,
      PROFILE_GIVEN_NAME, PROFILE_FAMILY_NAME, SIGNAL_PROFILE_AVATAR, PROFILE_SHARING, NOTIFICATION_CHANNEL,
      UNIDENTIFIED_ACCESS_MODE,
      FORCE_SMS_SELECTION,
      UUID_CAPABILITY, GROUPS_V2_CAPABILITY,
      STORAGE_SERVICE_ID, DIRTY
  };

  private static final String[] RECIPIENT_FULL_PROJECTION = ArrayUtils.concat(
      new String[] { TABLE_NAME + "." + ID },
      RECIPIENT_PROJECTION,
      new String[] {
        IdentityDatabase.TABLE_NAME + "." + IdentityDatabase.VERIFIED + " AS " + IDENTITY_STATUS,
        IdentityDatabase.TABLE_NAME + "." + IdentityDatabase.IDENTITY_KEY + " AS " + IDENTITY_KEY
      });


  public static final String[] CREATE_INDEXS = new String[] {
      "CREATE INDEX IF NOT EXISTS recipient_dirty_index ON " + TABLE_NAME + " (" + DIRTY + ");",
      "CREATE INDEX IF NOT EXISTS recipient_group_type_index ON " + TABLE_NAME + " (" + GROUP_TYPE + ");",
  };

  private static final String[]     ID_PROJECTION              = new String[]{ID};
  private static final String[]     SEARCH_PROJECTION          = new String[]{ID, SYSTEM_DISPLAY_NAME, PHONE, EMAIL, SYSTEM_PHONE_LABEL, SYSTEM_PHONE_TYPE, REGISTERED, "COALESCE(" + nullIfEmpty(PROFILE_JOINED_NAME) + ", " + nullIfEmpty(PROFILE_GIVEN_NAME) + ") AS " + SEARCH_PROFILE_NAME, "COALESCE(" + nullIfEmpty(SYSTEM_DISPLAY_NAME) + ", " + nullIfEmpty(PROFILE_JOINED_NAME) + ", " + nullIfEmpty(PROFILE_GIVEN_NAME) + ", " + nullIfEmpty(USERNAME) + ") AS " + SORT_NAME};
  public  static final String[]     SEARCH_PROJECTION_NAMES    = new String[]{ID, SYSTEM_DISPLAY_NAME, PHONE, EMAIL, SYSTEM_PHONE_LABEL, SYSTEM_PHONE_TYPE, REGISTERED, SEARCH_PROFILE_NAME, SORT_NAME};
          static final List<String> TYPED_RECIPIENT_PROJECTION = Stream.of(RECIPIENT_PROJECTION)
                                                                       .map(columnName -> TABLE_NAME + "." + columnName)
                                                                       .toList();

  public enum VibrateState {
    DEFAULT(0), ENABLED(1), DISABLED(2);

    private final int id;

    VibrateState(int id) {
      this.id = id;
    }

    public int getId() {
      return id;
    }

    public static VibrateState fromId(int id) {
      return values()[id];
    }
  }

  public enum RegisteredState {
    UNKNOWN(0), REGISTERED(1), NOT_REGISTERED(2);

    private final int id;

    RegisteredState(int id) {
      this.id = id;
    }

    public int getId() {
      return id;
    }

    public static RegisteredState fromId(int id) {
      return values()[id];
    }
  }

  public enum UnidentifiedAccessMode {
    UNKNOWN(0), DISABLED(1), ENABLED(2), UNRESTRICTED(3);

    private final int mode;

    UnidentifiedAccessMode(int mode) {
      this.mode = mode;
    }

    public int getMode() {
      return mode;
    }

    public static UnidentifiedAccessMode fromMode(int mode) {
      return values()[mode];
    }
  }

  public enum InsightsBannerTier {
    NO_TIER(0), TIER_ONE(1), TIER_TWO(2);

    private final int id;

    InsightsBannerTier(int id) {
      this.id = id;
    }

    public int getId() {
      return id;
    }

    public boolean seen(InsightsBannerTier tier) {
      return tier.getId() <= id;
    }

    public static InsightsBannerTier fromId(int id) {
      return values()[id];
    }
  }

  public enum DirtyState {
    CLEAN(0), UPDATE(1), INSERT(2), DELETE(3);

    private final int id;

    DirtyState(int id) {
      this.id = id;
    }

    int getId() {
      return id;
    }

    public static DirtyState fromId(int id) {
      return values()[id];
    }
  }

  public enum GroupType {
    NONE(0), MMS(1), SIGNAL_V1(2);

    private final int id;

    GroupType(int id) {
      this.id = id;
    }

    int getId() {
      return id;
    }

    public static GroupType fromId(int id) {
      return values()[id];
    }
  }

  public static final String CREATE_TABLE =
      "CREATE TABLE " + TABLE_NAME + " (" + ID                       + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                            UUID                     + " TEXT UNIQUE DEFAULT NULL, " +
                                            USERNAME                 + " TEXT UNIQUE DEFAULT NULL, " +
                                            PHONE                    + " TEXT UNIQUE DEFAULT NULL, " +
                                            EMAIL                    + " TEXT UNIQUE DEFAULT NULL, " +
                                            GROUP_ID                 + " TEXT UNIQUE DEFAULT NULL, " +
                                            GROUP_TYPE               + " INTEGER DEFAULT " + GroupType.NONE.getId() +  ", " +
                                            BLOCKED                  + " INTEGER DEFAULT 0," +
                                            MESSAGE_RINGTONE         + " TEXT DEFAULT NULL, " +
                                            MESSAGE_VIBRATE          + " INTEGER DEFAULT " + VibrateState.DEFAULT.getId() + ", " +
                                            CALL_RINGTONE            + " TEXT DEFAULT NULL, " +
                                            CALL_VIBRATE             + " INTEGER DEFAULT " + VibrateState.DEFAULT.getId() + ", " +
                                            NOTIFICATION_CHANNEL     + " TEXT DEFAULT NULL, " +
                                            MUTE_UNTIL               + " INTEGER DEFAULT 0, " +
                                            COLOR                    + " TEXT DEFAULT NULL, " +
                                            SEEN_INVITE_REMINDER     + " INTEGER DEFAULT " + InsightsBannerTier.NO_TIER.getId() + ", " +
                                            DEFAULT_SUBSCRIPTION_ID  + " INTEGER DEFAULT -1, " +
                                            MESSAGE_EXPIRATION_TIME  + " INTEGER DEFAULT 0, " +
                                            REGISTERED               + " INTEGER DEFAULT " + RegisteredState.UNKNOWN.getId() + ", " +
                                            SYSTEM_DISPLAY_NAME      + " TEXT DEFAULT NULL, " +
                                            SYSTEM_PHOTO_URI         + " TEXT DEFAULT NULL, " +
                                            SYSTEM_PHONE_LABEL       + " TEXT DEFAULT NULL, " +
                                            SYSTEM_PHONE_TYPE        + " INTEGER DEFAULT -1, " +
                                            SYSTEM_CONTACT_URI       + " TEXT DEFAULT NULL, " +
                                            SYSTEM_INFO_PENDING      + " INTEGER DEFAULT 0, " +
                                            PROFILE_KEY              + " TEXT DEFAULT NULL, " +
                                            PROFILE_KEY_CREDENTIAL   + " TEXT DEFAULT NULL, " +
                                            PROFILE_GIVEN_NAME       + " TEXT DEFAULT NULL, " +
                                            PROFILE_FAMILY_NAME      + " TEXT DEFAULT NULL, " +
                                            PROFILE_JOINED_NAME      + " TEXT DEFAULT NULL, " +
                                            SIGNAL_PROFILE_AVATAR    + " TEXT DEFAULT NULL, " +
                                            PROFILE_SHARING          + " INTEGER DEFAULT 0, " +
                                            UNIDENTIFIED_ACCESS_MODE + " INTEGER DEFAULT 0, " +
                                            FORCE_SMS_SELECTION      + " INTEGER DEFAULT 0, " +
                                            UUID_CAPABILITY          + " INTEGER DEFAULT " + Recipient.Capability.UNKNOWN.serialize() + ", " +
                                            GROUPS_V2_CAPABILITY     + " INTEGER DEFAULT " + Recipient.Capability.UNKNOWN.serialize() + ", " +
                                            STORAGE_SERVICE_ID       + " TEXT UNIQUE DEFAULT NULL, " +
                                            DIRTY                    + " INTEGER DEFAULT " + DirtyState.CLEAN.getId() + ");";

  private static final String INSIGHTS_INVITEE_LIST = "SELECT " + TABLE_NAME + "." + ID +
      " FROM " + TABLE_NAME +
      " INNER JOIN " + ThreadDatabase.TABLE_NAME +
      " ON " + TABLE_NAME + "." + ID + " = " + ThreadDatabase.TABLE_NAME + "." + ThreadDatabase.RECIPIENT_ID +
      " WHERE " +
      TABLE_NAME + "." + GROUP_ID + " IS NULL AND " +
      TABLE_NAME + "." + REGISTERED + " = " + RegisteredState.NOT_REGISTERED.id + " AND " +
      TABLE_NAME + "." + SEEN_INVITE_REMINDER + " < " + InsightsBannerTier.TIER_TWO.id + " AND " +
      ThreadDatabase.TABLE_NAME + "." + ThreadDatabase.HAS_SENT + " AND " +
      ThreadDatabase.TABLE_NAME + "." + ThreadDatabase.DATE + " > ?" +
      " ORDER BY " + ThreadDatabase.TABLE_NAME + "." + ThreadDatabase.DATE + " DESC LIMIT 50";

  public RecipientDatabase(Context context, SQLCipherOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  public @NonNull boolean containsPhoneOrUuid(@NonNull String id) {
    SQLiteDatabase db    = databaseHelper.getReadableDatabase();
    String         query = UUID + " = ? OR " + PHONE + " = ?";
    String[]       args  = new String[]{id, id};

    try (Cursor cursor = db.query(TABLE_NAME, new String[] { ID }, query, args, null, null, null)) {
      return cursor != null && cursor.moveToFirst();
    }
  }

  public @NonNull Optional<RecipientId> getByE164(@NonNull String e164) {
    return getByColumn(PHONE, e164);
  }

  public @NonNull Optional<RecipientId> getByEmail(@NonNull String email) {
    return getByColumn(EMAIL, email);
  }

  public @NonNull Optional<RecipientId> getByGroupId(@NonNull String groupId) {
    return getByColumn(GROUP_ID, groupId);

  }

  public @NonNull Optional<RecipientId> getByUuid(@NonNull UUID uuid) {
    return getByColumn(UUID, uuid.toString());
  }

  public @NonNull Optional<RecipientId> getByUsername(@NonNull String username) {
    return getByColumn(USERNAME, username);
  }

  public @NonNull RecipientId getOrInsertFromUuid(@NonNull UUID uuid) {
    return getOrInsertByColumn(UUID, uuid.toString()).recipientId;
  }

  public @NonNull RecipientId getOrInsertFromE164(@NonNull String e164) {
    return getOrInsertByColumn(PHONE, e164).recipientId;
  }

  public @NonNull RecipientId getOrInsertFromEmail(@NonNull String email) {
    return getOrInsertByColumn(EMAIL, email).recipientId;
  }

  public @NonNull RecipientId getOrInsertFromGroupId(@NonNull GroupId groupId) {
    GetOrInsertResult result = getOrInsertByColumn(GROUP_ID, groupId.toString());

    if (result.neededInsert) {
      ContentValues values = new ContentValues();

      if (groupId.isMms()) {
        values.put(GROUP_TYPE, GroupType.MMS.getId());
      } else {
        values.put(GROUP_TYPE, GroupType.SIGNAL_V1.getId());
        values.put(DIRTY, DirtyState.INSERT.getId());
        values.put(STORAGE_SERVICE_ID, Base64.encodeBytes(StorageSyncHelper.generateKey()));
      }

      update(result.recipientId, values);
    }

    return result.recipientId;
  }

  public Cursor getBlocked() {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();

    return database.query(TABLE_NAME, ID_PROJECTION, BLOCKED + " = 1",
                          null, null, null, null, null);
  }

  public RecipientReader readerForBlocked(Cursor cursor) {
    return new RecipientReader(cursor);
  }

  public RecipientReader getRecipientsWithNotificationChannels() {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    Cursor         cursor   = database.query(TABLE_NAME, ID_PROJECTION, NOTIFICATION_CHANNEL  + " NOT NULL",
                                             null, null, null, null, null);

    return new RecipientReader(cursor);
  }

  public @NonNull RecipientSettings getRecipientSettings(@NonNull RecipientId id) {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    String         table    = TABLE_NAME + " LEFT OUTER JOIN " + IdentityDatabase.TABLE_NAME + " ON " + TABLE_NAME + "." + ID + " = " + IdentityDatabase.TABLE_NAME + "." + IdentityDatabase.RECIPIENT_ID;
    String         query    = TABLE_NAME + "." + ID + " = ?";
    String[]       args     = new String[] { id.serialize() };

    try (Cursor cursor = database.query(table, RECIPIENT_FULL_PROJECTION, query, args, null, null, null)) {
      if (cursor != null && cursor.moveToNext()) {
        return getRecipientSettings(context, cursor);
      } else {
        throw new MissingRecipientError(id);
      }
    }
  }

  public @NonNull DirtyState getDirtyState(@NonNull RecipientId recipientId) {
    SQLiteDatabase db = databaseHelper.getReadableDatabase();

    try (Cursor cursor = db.query(TABLE_NAME,  new String[] { DIRTY }, ID_WHERE, new String[] { recipientId.serialize() }, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        return DirtyState.fromId(cursor.getInt(cursor.getColumnIndexOrThrow(DIRTY)));
      }
    }

    return DirtyState.CLEAN;
  }

  public @NonNull List<RecipientSettings> getPendingRecipientSyncUpdates() {
    String   query = DIRTY + " = ? AND " + STORAGE_SERVICE_ID + " NOT NULL AND " + TABLE_NAME + "." + ID + " != ?";
    String[] args  = new String[] { String.valueOf(DirtyState.UPDATE.getId()), Recipient.self().getId().serialize() };

    return getRecipientSettings(query, args);
  }

  public @NonNull List<RecipientSettings> getPendingRecipientSyncInsertions() {
    String   query = DIRTY + " = ? AND " + STORAGE_SERVICE_ID + " NOT NULL AND " + TABLE_NAME + "." + ID + " != ?";
    String[] args  = new String[] { String.valueOf(DirtyState.INSERT.getId()), Recipient.self().getId().serialize() };

    return getRecipientSettings(query, args);
  }

  public @NonNull List<RecipientSettings> getPendingRecipientSyncDeletions() {
    String   query = DIRTY + " = ? AND " + STORAGE_SERVICE_ID + " NOT NULL AND " + TABLE_NAME + "." + ID + " != ?";
    String[] args  = new String[] { String.valueOf(DirtyState.DELETE.getId()), Recipient.self().getId().serialize() };

    return getRecipientSettings(query, args);
  }

  public @Nullable RecipientSettings getByStorageId(@NonNull byte[] storageId) {
    List<RecipientSettings> result = getRecipientSettings(STORAGE_SERVICE_ID + " = ?", new String[] { Base64.encodeBytes(storageId) });

    if (result.size() > 0) {
      return result.get(0);
    }

    return null;
  }

  public void markNeedsSync(@NonNull RecipientId recipientId) {
    markDirty(recipientId, DirtyState.UPDATE);
  }

  public void applyStorageIdUpdates(@NonNull Map<RecipientId, StorageId> storageIds) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();

    db.beginTransaction();
    try {
      String query = ID + " = ?";

      for (Map.Entry<RecipientId, StorageId> entry : storageIds.entrySet()) {
        ContentValues values = new ContentValues();
        values.put(STORAGE_SERVICE_ID, Base64.encodeBytes(entry.getValue().getRaw()));
        values.put(DIRTY, DirtyState.CLEAN.getId());

        db.update(TABLE_NAME, values, query, new String[] { entry.getKey().serialize() });
      }
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public void applyStorageSyncUpdates(@NonNull Collection<SignalContactRecord>               contactInserts,
                                      @NonNull Collection<RecordUpdate<SignalContactRecord>> contactUpdates,
                                      @NonNull Collection<SignalGroupV1Record>               groupV1Inserts,
                                      @NonNull Collection<RecordUpdate<SignalGroupV1Record>> groupV1Updates)
  {
    SQLiteDatabase   db               = databaseHelper.getWritableDatabase();
    IdentityDatabase identityDatabase = DatabaseFactory.getIdentityDatabase(context);
    ThreadDatabase   threadDatabase   = DatabaseFactory.getThreadDatabase(context);

    db.beginTransaction();

    try {

      for (SignalContactRecord insert : contactInserts) {
        ContentValues values = getValuesForStorageContact(insert);
        long          id     = db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_IGNORE);

        if (id < 0) {
          Log.w(TAG, "Failed to insert! It's likely that these were newly-registered users that were missed in the merge. Doing an update instead.");

          if (insert.getAddress().getNumber().isPresent()) {
            int count = db.update(TABLE_NAME, values, PHONE + " = ?", new String[] { insert.getAddress().getNumber().get() });
            Log.w(TAG, "Updated " + count + " users by E164.");
          } else {
            int count = db.update(TABLE_NAME, values, UUID + " = ?", new String[] { insert.getAddress().getUuid().get().toString() });
            Log.w(TAG, "Updated " + count + " users by UUID.");
          }
        } else {
          RecipientId recipientId = RecipientId.from(id);

          if (insert.getIdentityKey().isPresent()) {
            try {
              IdentityKey identityKey = new IdentityKey(insert.getIdentityKey().get(), 0);

              DatabaseFactory.getIdentityDatabase(context).updateIdentityAfterSync(recipientId, identityKey, StorageSyncModels.remoteToLocalIdentityStatus(insert.getIdentityState()));
              IdentityUtil.markIdentityVerified(context, Recipient.resolved(recipientId), true, true);
            } catch (InvalidKeyException e) {
              Log.w(TAG, "Failed to process identity key during insert! Skipping.", e);
            }
          }

          threadDatabase.setArchived(recipientId, insert.isArchived());
          Recipient.live(recipientId).refresh();
        }
      }

      for (RecordUpdate<SignalContactRecord> update : contactUpdates) {
        ContentValues values      = getValuesForStorageContact(update.getNew());
        int           updateCount = db.update(TABLE_NAME, values, STORAGE_SERVICE_ID + " = ?", new String[]{Base64.encodeBytes(update.getOld().getId().getRaw())});

        if (updateCount < 1) {
          throw new AssertionError("Had an update, but it didn't match any rows!");
        }

        RecipientId recipientId = getByStorageKeyOrThrow(update.getNew().getId().getRaw());

        if (StorageSyncHelper.profileKeyChanged(update)) {
          clearProfileKeyCredential(recipientId);
        }

        try {
          Optional<IdentityRecord> oldIdentityRecord = identityDatabase.getIdentity(recipientId);

          if (update.getNew().getIdentityKey().isPresent()) {
            IdentityKey identityKey = new IdentityKey(update.getNew().getIdentityKey().get(), 0);
            DatabaseFactory.getIdentityDatabase(context).updateIdentityAfterSync(recipientId, identityKey, StorageSyncModels.remoteToLocalIdentityStatus(update.getNew().getIdentityState()));
          }

          Optional<IdentityRecord> newIdentityRecord = identityDatabase.getIdentity(recipientId);

          if ((newIdentityRecord.isPresent() && newIdentityRecord.get().getVerifiedStatus() == IdentityDatabase.VerifiedStatus.VERIFIED) &&
              (!oldIdentityRecord.isPresent() || oldIdentityRecord.get().getVerifiedStatus() != IdentityDatabase.VerifiedStatus.VERIFIED))
          {
            IdentityUtil.markIdentityVerified(context, Recipient.resolved(recipientId), true, true);
          } else if ((newIdentityRecord.isPresent() && newIdentityRecord.get().getVerifiedStatus() != IdentityDatabase.VerifiedStatus.VERIFIED) &&
                     (oldIdentityRecord.isPresent() && oldIdentityRecord.get().getVerifiedStatus() == IdentityDatabase.VerifiedStatus.VERIFIED))
          {
            IdentityUtil.markIdentityVerified(context, Recipient.resolved(recipientId), false, true);
          }
        } catch (InvalidKeyException e) {
          Log.w(TAG, "Failed to process identity key during update! Skipping.", e);
        }

        threadDatabase.setArchived(recipientId, update.getNew().isArchived());
        Recipient.live(recipientId).refresh();
      }

      for (SignalGroupV1Record insert : groupV1Inserts) {
        db.insertOrThrow(TABLE_NAME, null, getValuesForStorageGroupV1(insert));

        Recipient recipient = Recipient.externalGroup(context, GroupId.v1orThrow(insert.getGroupId()));

        threadDatabase.setArchived(recipient.getId(), insert.isArchived());
        recipient.live().refresh();
      }

      for (RecordUpdate<SignalGroupV1Record> update : groupV1Updates) {
        ContentValues values      = getValuesForStorageGroupV1(update.getNew());
        int           updateCount = db.update(TABLE_NAME, values, STORAGE_SERVICE_ID + " = ?", new String[]{Base64.encodeBytes(update.getOld().getId().getRaw())});

        if (updateCount < 1) {
          throw new AssertionError("Had an update, but it didn't match any rows!");
        }

        Recipient recipient = Recipient.externalGroup(context, GroupId.v1orThrow(update.getOld().getGroupId()));

        threadDatabase.setArchived(recipient.getId(), update.getNew().isArchived());
        recipient.live().refresh();
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public void applyStorageSyncUpdates(@NonNull StorageId storageId, SignalAccountRecord update) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();

    ContentValues       values      = new ContentValues();
    ProfileName         profileName = ProfileName.fromParts(update.getGivenName().orNull(), update.getFamilyName().orNull());

    values.put(PROFILE_GIVEN_NAME, profileName.getGivenName());
    values.put(PROFILE_FAMILY_NAME, profileName.getFamilyName());
    values.put(PROFILE_JOINED_NAME, profileName.toString());
    values.put(PROFILE_KEY, update.getProfileKey().transform(Base64::encodeBytes).orNull());
    values.put(STORAGE_SERVICE_ID, Base64.encodeBytes(update.getId().getRaw()));
    values.put(DIRTY, DirtyState.CLEAN.getId());

    int updateCount = db.update(TABLE_NAME, values, STORAGE_SERVICE_ID + " = ?", new String[]{Base64.encodeBytes(storageId.getRaw())});
    if (updateCount < 1) {
      throw new AssertionError("Account update didn't match any rows!");
    }

    Recipient.self().live().refresh();
  }

  public void updatePhoneNumbers(@NonNull Map<String, String> mapping) {
    if (mapping.isEmpty()) return;

    SQLiteDatabase db = databaseHelper.getWritableDatabase();

    db.beginTransaction();
    try {
      String query = PHONE + " = ?";

      for (Map.Entry<String, String> entry : mapping.entrySet()) {
        ContentValues values = new ContentValues();
        values.put(PHONE, entry.getValue());

        db.updateWithOnConflict(TABLE_NAME, values, query, new String[] { entry.getKey() }, SQLiteDatabase.CONFLICT_IGNORE);
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  private @NonNull RecipientId getByStorageKeyOrThrow(byte[] storageKey) {
    SQLiteDatabase db    = databaseHelper.getReadableDatabase();
    String         query = STORAGE_SERVICE_ID + " = ?";
    String[]       args  = new String[]{Base64.encodeBytes(storageKey)};

    try (Cursor cursor = db.query(TABLE_NAME, ID_PROJECTION, query, args, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        long id = cursor.getLong(cursor.getColumnIndexOrThrow(ID));
        return RecipientId.from(id);
      } else {
        throw new AssertionError("No recipient with that storage key!");
      }
    }
  }

  private static @NonNull ContentValues getValuesForStorageContact(@NonNull SignalContactRecord contact) {
    ContentValues values = new ContentValues();

    if (contact.getAddress().getUuid().isPresent()) {
      values.put(UUID, contact.getAddress().getUuid().get().toString());
    }

    ProfileName profileName = ProfileName.fromParts(contact.getGivenName().orNull(), contact.getFamilyName().orNull());
    String      username    = contact.getUsername().orNull();

    values.put(PHONE, contact.getAddress().getNumber().orNull());
    values.put(PROFILE_GIVEN_NAME, profileName.getGivenName());
    values.put(PROFILE_FAMILY_NAME, profileName.getFamilyName());
    values.put(PROFILE_JOINED_NAME, profileName.toString());
    values.put(PROFILE_KEY, contact.getProfileKey().transform(Base64::encodeBytes).orNull());
    values.put(USERNAME, TextUtils.isEmpty(username) ? null : username);
    values.put(PROFILE_SHARING, contact.isProfileSharingEnabled() ? "1" : "0");
    values.put(BLOCKED, contact.isBlocked() ? "1" : "0");
    values.put(STORAGE_SERVICE_ID, Base64.encodeBytes(contact.getId().getRaw()));
    values.put(DIRTY, DirtyState.CLEAN.getId());
    return values;
  }

  private static @NonNull ContentValues getValuesForStorageGroupV1(@NonNull SignalGroupV1Record groupV1) {
    ContentValues values = new ContentValues();
    values.put(GROUP_ID, GroupId.v1orThrow(groupV1.getGroupId()).toString());
    values.put(GROUP_TYPE, GroupType.SIGNAL_V1.getId());
    values.put(PROFILE_SHARING, groupV1.isProfileSharingEnabled() ? "1" : "0");
    values.put(BLOCKED, groupV1.isBlocked() ? "1" : "0");
    values.put(STORAGE_SERVICE_ID, Base64.encodeBytes(groupV1.getId().getRaw()));
    values.put(DIRTY, DirtyState.CLEAN.getId());
    return values;
  }

  private List<RecipientSettings> getRecipientSettings(@Nullable String query, @Nullable String[] args) {
    SQLiteDatabase          db    = databaseHelper.getReadableDatabase();
    String                  table = TABLE_NAME + " LEFT OUTER JOIN " + IdentityDatabase.TABLE_NAME + " ON " + TABLE_NAME + "." + ID + " = " + IdentityDatabase.TABLE_NAME + "." + IdentityDatabase.RECIPIENT_ID;
    List<RecipientSettings> out   = new ArrayList<>();

    try (Cursor cursor = db.query(table, RECIPIENT_FULL_PROJECTION, query, args, null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        out.add(getRecipientSettings(context, cursor));
      }
    }

    return out;
  }

  /**
   * @return All storage ids for ContactRecords, excluding the ones that need to be deleted.
   */
  public List<StorageId> getContactStorageSyncIds() {
    return new ArrayList<>(getContactStorageSyncIdsMap().values());
  }

  /**
   * @return All storage IDs for ContactRecords, excluding the ones that need to be deleted.
   */
  public @NonNull Map<RecipientId, StorageId> getContactStorageSyncIdsMap() {
    SQLiteDatabase              db    = databaseHelper.getReadableDatabase();
    String                      query = STORAGE_SERVICE_ID + " NOT NULL AND " + DIRTY + " != ? AND " + ID + " != ?";
    String[]                    args  = new String[]{String.valueOf(DirtyState.DELETE), Recipient.self().getId().serialize() };
    Map<RecipientId, StorageId> out   = new HashMap<>();

    try (Cursor cursor = db.query(TABLE_NAME, new String[] { ID, STORAGE_SERVICE_ID, GROUP_TYPE }, query, args, null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        RecipientId id         = RecipientId.from(cursor.getLong(cursor.getColumnIndexOrThrow(ID)));
        String      encodedKey = cursor.getString(cursor.getColumnIndexOrThrow(STORAGE_SERVICE_ID));
        GroupType   groupType  = GroupType.fromId(cursor.getInt(cursor.getColumnIndexOrThrow(GROUP_TYPE)));
        byte[]      key        = Base64.decodeOrThrow(encodedKey);

        if (groupType == GroupType.NONE) {
          out.put(id, StorageId.forContact(key));
        } else {
          out.put(id, StorageId.forGroupV1(key));
        }
      }
    }

    return out;
  }

  private static @NonNull RecipientSettings getRecipientSettings(@NonNull Context context, @NonNull Cursor cursor) {
    long    id                         = cursor.getLong(cursor.getColumnIndexOrThrow(ID));
    UUID    uuid                       = UuidUtil.parseOrNull(cursor.getString(cursor.getColumnIndexOrThrow(UUID)));
    String  username                   = cursor.getString(cursor.getColumnIndexOrThrow(USERNAME));
    String  e164                       = cursor.getString(cursor.getColumnIndexOrThrow(PHONE));
    String  email                      = cursor.getString(cursor.getColumnIndexOrThrow(EMAIL));
    GroupId groupId                    = GroupId.parseNullableOrThrow(cursor.getString(cursor.getColumnIndexOrThrow(GROUP_ID)));
    int     groupType                  = cursor.getInt(cursor.getColumnIndexOrThrow(GROUP_TYPE));
    boolean blocked                    = cursor.getInt(cursor.getColumnIndexOrThrow(BLOCKED))                == 1;
    String  messageRingtone            = cursor.getString(cursor.getColumnIndexOrThrow(MESSAGE_RINGTONE));
    String  callRingtone               = cursor.getString(cursor.getColumnIndexOrThrow(CALL_RINGTONE));
    int     messageVibrateState        = cursor.getInt(cursor.getColumnIndexOrThrow(MESSAGE_VIBRATE));
    int     callVibrateState           = cursor.getInt(cursor.getColumnIndexOrThrow(CALL_VIBRATE));
    long    muteUntil                  = cursor.getLong(cursor.getColumnIndexOrThrow(MUTE_UNTIL));
    String  serializedColor            = cursor.getString(cursor.getColumnIndexOrThrow(COLOR));
    int     insightsBannerTier         = cursor.getInt(cursor.getColumnIndexOrThrow(SEEN_INVITE_REMINDER));
    int     defaultSubscriptionId      = cursor.getInt(cursor.getColumnIndexOrThrow(DEFAULT_SUBSCRIPTION_ID));
    int     expireMessages             = cursor.getInt(cursor.getColumnIndexOrThrow(MESSAGE_EXPIRATION_TIME));
    int     registeredState            = cursor.getInt(cursor.getColumnIndexOrThrow(REGISTERED));
    String  profileKeyString           = cursor.getString(cursor.getColumnIndexOrThrow(PROFILE_KEY));
    String  profileKeyCredentialString = cursor.getString(cursor.getColumnIndexOrThrow(PROFILE_KEY_CREDENTIAL));
    String  systemDisplayName          = cursor.getString(cursor.getColumnIndexOrThrow(SYSTEM_DISPLAY_NAME));
    String  systemContactPhoto         = cursor.getString(cursor.getColumnIndexOrThrow(SYSTEM_PHOTO_URI));
    String  systemPhoneLabel           = cursor.getString(cursor.getColumnIndexOrThrow(SYSTEM_PHONE_LABEL));
    String  systemContactUri           = cursor.getString(cursor.getColumnIndexOrThrow(SYSTEM_CONTACT_URI));
    String  profileGivenName           = cursor.getString(cursor.getColumnIndexOrThrow(PROFILE_GIVEN_NAME));
    String  profileFamilyName          = cursor.getString(cursor.getColumnIndexOrThrow(PROFILE_FAMILY_NAME));
    String  signalProfileAvatar        = cursor.getString(cursor.getColumnIndexOrThrow(SIGNAL_PROFILE_AVATAR));
    boolean profileSharing             = cursor.getInt(cursor.getColumnIndexOrThrow(PROFILE_SHARING))      == 1;
    String  notificationChannel        = cursor.getString(cursor.getColumnIndexOrThrow(NOTIFICATION_CHANNEL));
    int     unidentifiedAccessMode     = cursor.getInt(cursor.getColumnIndexOrThrow(UNIDENTIFIED_ACCESS_MODE));
    boolean forceSmsSelection          = cursor.getInt(cursor.getColumnIndexOrThrow(FORCE_SMS_SELECTION))  == 1;
    int     uuidCapabilityValue        = cursor.getInt(cursor.getColumnIndexOrThrow(UUID_CAPABILITY));
    int     groupsV2CapabilityValue    = cursor.getInt(cursor.getColumnIndexOrThrow(GROUPS_V2_CAPABILITY));
    String  storageKeyRaw              = cursor.getString(cursor.getColumnIndexOrThrow(STORAGE_SERVICE_ID));
    String  identityKeyRaw             = cursor.getString(cursor.getColumnIndexOrThrow(IDENTITY_KEY));
    int     identityStatusRaw          = cursor.getInt(cursor.getColumnIndexOrThrow(IDENTITY_STATUS));

    MaterialColor color;
    byte[]        profileKey           = null;
    byte[]        profileKeyCredential = null;

    try {
      color = serializedColor == null ? null : MaterialColor.fromSerialized(serializedColor);
    } catch (MaterialColor.UnknownColorException e) {
      Log.w(TAG, e);
      color = null;
    }

    if (profileKeyString != null) {
      try {
        profileKey = Base64.decode(profileKeyString);
      } catch (IOException e) {
        Log.w(TAG, e);
        profileKey = null;
      }

      if (profileKeyCredentialString != null) {
        try {
          profileKeyCredential = Base64.decode(profileKeyCredentialString);
        } catch (IOException e) {
          Log.w(TAG, e);
          profileKeyCredential = null;
        }
      }
    }

    byte[] storageKey  = storageKeyRaw != null ? Base64.decodeOrThrow(storageKeyRaw) : null;
    byte[] identityKey = identityKeyRaw != null ? Base64.decodeOrThrow(identityKeyRaw) : null;

    IdentityDatabase.VerifiedStatus identityStatus = IdentityDatabase.VerifiedStatus.forState(identityStatusRaw);

    return new RecipientSettings(RecipientId.from(id), uuid, username, e164, email, groupId, GroupType.fromId(groupType), blocked, muteUntil,
                                 VibrateState.fromId(messageVibrateState),
                                 VibrateState.fromId(callVibrateState),
                                 Util.uri(messageRingtone), Util.uri(callRingtone),
                                 color, defaultSubscriptionId, expireMessages,
                                 RegisteredState.fromId(registeredState),
                                 profileKey, profileKeyCredential,
                                 systemDisplayName, systemContactPhoto,
                                 systemPhoneLabel, systemContactUri,
                                 ProfileName.fromParts(profileGivenName, profileFamilyName), signalProfileAvatar,
                                 AvatarHelper.hasAvatar(context, RecipientId.from(id)), profileSharing,
                                 notificationChannel, UnidentifiedAccessMode.fromMode(unidentifiedAccessMode),
                                 forceSmsSelection,
                                 Recipient.Capability.deserialize(uuidCapabilityValue),
                                 Recipient.Capability.deserialize(groupsV2CapabilityValue),
                                 InsightsBannerTier.fromId(insightsBannerTier),
                                 storageKey, identityKey, identityStatus);
  }

  public BulkOperationsHandle beginBulkSystemContactUpdate() {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    database.beginTransaction();

    ContentValues contentValues = new ContentValues(1);
    contentValues.put(SYSTEM_INFO_PENDING, 1);

    database.update(TABLE_NAME, contentValues, SYSTEM_CONTACT_URI + " NOT NULL", null);

    return new BulkOperationsHandle(database);
  }

  public void setColor(@NonNull RecipientId id, @NonNull MaterialColor color) {
    ContentValues values = new ContentValues();
    values.put(COLOR, color.serialize());
    if (update(id, values)) {
      Recipient.live(id).refresh();
    }
  }

  public void setDefaultSubscriptionId(@NonNull RecipientId id, int defaultSubscriptionId) {
    ContentValues values = new ContentValues();
    values.put(DEFAULT_SUBSCRIPTION_ID, defaultSubscriptionId);
    if (update(id, values)) {
      Recipient.live(id).refresh();
    }
  }

  public void setForceSmsSelection(@NonNull RecipientId id, boolean forceSmsSelection) {
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(FORCE_SMS_SELECTION, forceSmsSelection ? 1 : 0);
    if (update(id, contentValues)) {
      Recipient.live(id).refresh();
    }
  }

  public void setBlocked(@NonNull RecipientId id, boolean blocked) {
    ContentValues values = new ContentValues();
    values.put(BLOCKED, blocked ? 1 : 0);
    if (update(id, values)) {
      markDirty(id, DirtyState.UPDATE);
      Recipient.live(id).refresh();
    }
  }

  public void setMessageRingtone(@NonNull RecipientId id, @Nullable Uri notification) {
    ContentValues values = new ContentValues();
    values.put(MESSAGE_RINGTONE, notification == null ? null : notification.toString());
    if (update(id, values)) {
      Recipient.live(id).refresh();
    }
  }

  public void setCallRingtone(@NonNull RecipientId id, @Nullable Uri ringtone) {
    ContentValues values = new ContentValues();
    values.put(CALL_RINGTONE, ringtone == null ? null : ringtone.toString());
    if (update(id, values)) {
      Recipient.live(id).refresh();
    }
  }

  public void setMessageVibrate(@NonNull RecipientId id, @NonNull VibrateState enabled) {
    ContentValues values = new ContentValues();
    values.put(MESSAGE_VIBRATE, enabled.getId());
    if (update(id, values)) {
      Recipient.live(id).refresh();
    }
  }

  public void setCallVibrate(@NonNull RecipientId id, @NonNull VibrateState enabled) {
    ContentValues values = new ContentValues();
    values.put(CALL_VIBRATE, enabled.getId());
    if (update(id, values)) {
      Recipient.live(id).refresh();
    }
  }

  public void setMuted(@NonNull RecipientId id, long until) {
    ContentValues values = new ContentValues();
    values.put(MUTE_UNTIL, until);
    if (update(id, values)) {
      Recipient.live(id).refresh();
    }
  }

  public void setSeenFirstInviteReminder(@NonNull RecipientId id) {
    setInsightsBannerTier(id, InsightsBannerTier.TIER_ONE);
  }

  public void setSeenSecondInviteReminder(@NonNull RecipientId id) {
    setInsightsBannerTier(id, InsightsBannerTier.TIER_TWO);
  }

  public void setHasSentInvite(@NonNull RecipientId id) {
    setSeenSecondInviteReminder(id);
  }

  private void setInsightsBannerTier(@NonNull RecipientId id, @NonNull InsightsBannerTier insightsBannerTier) {
    SQLiteDatabase database  = databaseHelper.getWritableDatabase();
    ContentValues  values    = new ContentValues(1);
    String         query     = ID + " = ? AND " + SEEN_INVITE_REMINDER + " < ?";
    String[]       args      = new String[]{ id.serialize(), String.valueOf(insightsBannerTier) };

    values.put(SEEN_INVITE_REMINDER, insightsBannerTier.id);
    database.update(TABLE_NAME, values, query, args);
    Recipient.live(id).refresh();
  }

  public void setExpireMessages(@NonNull RecipientId id, int expiration) {
    ContentValues values = new ContentValues(1);
    values.put(MESSAGE_EXPIRATION_TIME, expiration);
    update(id, values);
    Recipient.live(id).refresh();
  }

  public void setUnidentifiedAccessMode(@NonNull RecipientId id, @NonNull UnidentifiedAccessMode unidentifiedAccessMode) {
    ContentValues values = new ContentValues(1);
    values.put(UNIDENTIFIED_ACCESS_MODE, unidentifiedAccessMode.getMode());
    if (update(id, values)) {
      markDirty(id, DirtyState.UPDATE);
    }
    Recipient.live(id).refresh();
  }

  public void setCapabilities(@NonNull RecipientId id, @NonNull SignalServiceProfile.Capabilities capabilities) {
    ContentValues values = new ContentValues(2);
    values.put(UUID_CAPABILITY,      Recipient.Capability.fromBoolean(capabilities.isUuid()).serialize());
    values.put(GROUPS_V2_CAPABILITY, Recipient.Capability.fromBoolean(capabilities.isGv2()).serialize());
    update(id, values);
    Recipient.live(id).refresh();
  }

  /**
   * Updates the profile key.
   * <p>
   * If it changes, it clears out the profile key credential and resets the unidentified access mode.
   * @return true iff changed.
   */
  public boolean setProfileKey(@NonNull RecipientId id, @NonNull ProfileKey profileKey) {
    String        selection         = ID + " = ?";
    String[]      args              = new String[]{id.serialize()};
    ContentValues valuesToCompare   = new ContentValues(1);
    ContentValues valuesToSet       = new ContentValues(3);
    String        encodedProfileKey = Base64.encodeBytes(profileKey.serialize());

    valuesToCompare.put(PROFILE_KEY, encodedProfileKey);

    valuesToSet.put(PROFILE_KEY, encodedProfileKey);
    valuesToSet.putNull(PROFILE_KEY_CREDENTIAL);
    valuesToSet.put(UNIDENTIFIED_ACCESS_MODE, UnidentifiedAccessMode.UNKNOWN.getMode());

    SqlUtil.UpdateQuery updateQuery = SqlUtil.buildTrueUpdateQuery(selection, args, valuesToCompare);

    if (update(updateQuery, valuesToSet)) {
      markDirty(id, DirtyState.UPDATE);
      Recipient.live(id).refresh();
      StorageSyncHelper.scheduleSyncForDataChange();
      return true;
    } else {
      return false;
    }
  }

  /**
   * Updates the profile key credential as long as the profile key matches.
   */
  public void setProfileKeyCredential(@NonNull RecipientId id,
                                      @NonNull ProfileKey profileKey,
                                      @NonNull ProfileKeyCredential profileKeyCredential)
  {
    String        selection = ID + " = ? AND " + PROFILE_KEY + " = ?";
    String[]      args      = new String[]{id.serialize(), Base64.encodeBytes(profileKey.serialize())};
    ContentValues values    = new ContentValues(1);

    values.put(PROFILE_KEY_CREDENTIAL, Base64.encodeBytes(profileKeyCredential.serialize()));

    SqlUtil.UpdateQuery updateQuery = SqlUtil.buildTrueUpdateQuery(selection, args, values);

    if (update(updateQuery, values)) {
      // TODO [greyson] If we sync this in future, mark dirty
      //markDirty(id, DirtyState.UPDATE);
      Recipient.live(id).refresh();
    }
  }

  private void clearProfileKeyCredential(@NonNull RecipientId id) {
    ContentValues values = new ContentValues(1);
    values.putNull(PROFILE_KEY_CREDENTIAL);
    if (update(id, values)) {
      markDirty(id, DirtyState.UPDATE);
      Recipient.live(id).refresh();
    }
  }

  public void setProfileName(@NonNull RecipientId id, @NonNull ProfileName profileName) {
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(PROFILE_GIVEN_NAME, profileName.getGivenName());
    contentValues.put(PROFILE_FAMILY_NAME, profileName.getFamilyName());
    contentValues.put(PROFILE_JOINED_NAME, profileName.toString());
    if (update(id, contentValues)) {
      markDirty(id, DirtyState.UPDATE);
      Recipient.live(id).refresh();
      StorageSyncHelper.scheduleSyncForDataChange();
    }
  }

  public void setProfileAvatar(@NonNull RecipientId id, @Nullable String profileAvatar) {
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(SIGNAL_PROFILE_AVATAR, profileAvatar);
    if (update(id, contentValues)) {
      Recipient.live(id).refresh();

      if (id.equals(Recipient.self().getId())) {
        markDirty(id, DirtyState.UPDATE);
        StorageSyncHelper.scheduleSyncForDataChange();
      }
    }
  }

  public void setProfileSharing(@NonNull RecipientId id, @SuppressWarnings("SameParameterValue") boolean enabled) {
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(PROFILE_SHARING, enabled ? 1 : 0);
    if (update(id, contentValues)) {
      markDirty(id, DirtyState.UPDATE);
      Recipient.live(id).refresh();
      StorageSyncHelper.scheduleSyncForDataChange();
    }
  }

  public void setNotificationChannel(@NonNull RecipientId id, @Nullable String notificationChannel) {
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(NOTIFICATION_CHANNEL, notificationChannel);
    if (update(id, contentValues)) {
      Recipient.live(id).refresh();
    }
  }

  public void setPhoneNumber(@NonNull RecipientId id, @NonNull String e164) {
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(PHONE, e164);
    if (update(id, contentValues)) {
      markDirty(id, DirtyState.UPDATE);
      Recipient.live(id).refresh();
      StorageSyncHelper.scheduleSyncForDataChange();
    }
  }

  public void setUsername(@NonNull RecipientId id, @Nullable String username) {
    if (username != null) {
      Optional<RecipientId> existingUsername = getByUsername(username);

      if (existingUsername.isPresent() && !id.equals(existingUsername.get())) {
        Log.i(TAG, "Username was previously thought to be owned by " + existingUsername.get() + ". Clearing their username.");
        setUsername(existingUsername.get(), null);
      }
    }

    ContentValues contentValues = new ContentValues(1);
    contentValues.put(USERNAME, username);
    if (update(id, contentValues)) {
      Recipient.live(id).refresh();
      StorageSyncHelper.scheduleSyncForDataChange();
    }
  }

  public void clearUsernameIfExists(@NonNull String username) {
    Optional<RecipientId> existingUsername = getByUsername(username);

    if (existingUsername.isPresent()) {
      setUsername(existingUsername.get(), null);
    }
  }

  public Set<String> getAllPhoneNumbers() {
    SQLiteDatabase db      = databaseHelper.getReadableDatabase();
    Set<String>    results = new HashSet<>();

    try (Cursor cursor = db.query(TABLE_NAME, new String[] { PHONE }, null, null, null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        String number = cursor.getString(cursor.getColumnIndexOrThrow(PHONE));

        if (!TextUtils.isEmpty(number)) {
          results.add(number);
        }
      }
    }

    return results;
  }

  public void markRegistered(@NonNull RecipientId id, @NonNull UUID uuid) {
    ContentValues contentValues = new ContentValues(3);
    contentValues.put(REGISTERED, RegisteredState.REGISTERED.getId());
    contentValues.put(UUID, uuid.toString().toLowerCase());
    if (update(id, contentValues)) {
      markDirty(id, DirtyState.INSERT);
      Recipient.live(id).refresh();
    }
  }

  /**
   * Marks the user as registered without providing a UUID. This should only be used when one
   * cannot be reasonably obtained. {@link #markRegistered(RecipientId, UUID)} should be strongly
   * preferred.
   */
  public void markRegistered(@NonNull RecipientId id) {
    ContentValues contentValues = new ContentValues(2);
    contentValues.put(REGISTERED, RegisteredState.REGISTERED.getId());
    if (update(id, contentValues)) {
      markDirty(id, DirtyState.INSERT);
      Recipient.live(id).refresh();
    }
  }

  public void markUnregistered(@NonNull RecipientId id) {
    ContentValues contentValues = new ContentValues(2);
    contentValues.put(REGISTERED, RegisteredState.NOT_REGISTERED.getId());
    contentValues.put(UUID, (String) null);
    if (update(id, contentValues)) {
      markDirty(id, DirtyState.DELETE);
      Recipient.live(id).refresh();
    }
  }

  public void bulkUpdatedRegisteredStatus(@NonNull Map<RecipientId, String> registered, Collection<RecipientId> unregistered) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.beginTransaction();

    try {
      for (Map.Entry<RecipientId, String> entry : registered.entrySet()) {
        ContentValues values = new ContentValues(2);
        values.put(REGISTERED, RegisteredState.REGISTERED.getId());
        values.put(UUID, entry.getValue().toLowerCase());
        if (update(entry.getKey(), values)) {
          markDirty(entry.getKey(), DirtyState.INSERT);
        }
      }

      for (RecipientId id : unregistered) {
        ContentValues values = new ContentValues(1);
        values.put(REGISTERED, RegisteredState.NOT_REGISTERED.getId());
        values.put(UUID, (String) null);
        if (update(id, values)) {
          markDirty(id, DirtyState.DELETE);
        }
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  @Deprecated
  public void setRegistered(@NonNull RecipientId id, RegisteredState registeredState) {
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(REGISTERED, registeredState.getId());

    if (update(id, contentValues)) {
      if (registeredState == RegisteredState.REGISTERED) {
        markDirty(id, DirtyState.INSERT);
      } else if (registeredState == RegisteredState.NOT_REGISTERED) {
        markDirty(id, DirtyState.DELETE);
      }

      Recipient.live(id).refresh();
    }
  }

  @Deprecated
  public void setRegistered(@NonNull Collection<RecipientId> activeIds,
                            @NonNull Collection<RecipientId> inactiveIds)
  {
    for (RecipientId activeId : activeIds) {
      ContentValues registeredValues = new ContentValues(1);
      registeredValues.put(REGISTERED, RegisteredState.REGISTERED.getId());

      if (update(activeId, registeredValues)) {
        markDirty(activeId, DirtyState.INSERT);
        Recipient.live(activeId).refresh();
      }
    }

    for (RecipientId inactiveId : inactiveIds) {
      ContentValues contentValues = new ContentValues(1);
      contentValues.put(REGISTERED, RegisteredState.NOT_REGISTERED.getId());

      if (update(inactiveId, contentValues)) {
        markDirty(inactiveId, DirtyState.DELETE);
        Recipient.live(inactiveId).refresh();
      }
    }
  }

  public @NonNull List<RecipientId> getUninvitedRecipientsForInsights() {
    SQLiteDatabase    db      = databaseHelper.getReadableDatabase();
    List<RecipientId> results = new LinkedList<>();
    final String[]    args    = new String[]{String.valueOf(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(31))};

    try (Cursor cursor = db.rawQuery(INSIGHTS_INVITEE_LIST, args)) {
      while (cursor != null && cursor.moveToNext()) {
        results.add(RecipientId.from(cursor.getLong(cursor.getColumnIndexOrThrow(ID))));
      }
    }

    return results;
  }

  public @NonNull List<RecipientId> getRegistered() {
    SQLiteDatabase    db      = databaseHelper.getReadableDatabase();
    List<RecipientId> results = new LinkedList<>();

    try (Cursor cursor = db.query(TABLE_NAME, ID_PROJECTION, REGISTERED + " = ?", new String[] {"1"}, null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        results.add(RecipientId.from(cursor.getLong(cursor.getColumnIndexOrThrow(ID))));
      }
    }

    return results;
  }

  public List<RecipientId> getSystemContacts() {
    SQLiteDatabase    db      = databaseHelper.getReadableDatabase();
    List<RecipientId> results = new LinkedList<>();

    try (Cursor cursor = db.query(TABLE_NAME, ID_PROJECTION, SYSTEM_DISPLAY_NAME + " IS NOT NULL AND " + SYSTEM_DISPLAY_NAME + " != \"\"", null, null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        results.add(RecipientId.from(cursor.getLong(cursor.getColumnIndexOrThrow(ID))));
      }
    }

    return results;
  }

  public void updateSystemContactColors(@NonNull ColorUpdater updater) {
    SQLiteDatabase                  db      = databaseHelper.getReadableDatabase();
    Map<RecipientId, MaterialColor> updates = new HashMap<>();

    db.beginTransaction();
    try (Cursor cursor = db.query(TABLE_NAME, new String[] {ID, COLOR, SYSTEM_DISPLAY_NAME}, SYSTEM_DISPLAY_NAME + " IS NOT NULL AND " + SYSTEM_DISPLAY_NAME + " != \"\"", null, null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        long          id       = cursor.getLong(cursor.getColumnIndexOrThrow(ID));
        MaterialColor newColor = updater.update(cursor.getString(cursor.getColumnIndexOrThrow(SYSTEM_DISPLAY_NAME)),
                                                cursor.getString(cursor.getColumnIndexOrThrow(COLOR)));

        ContentValues contentValues = new ContentValues(1);
        contentValues.put(COLOR, newColor.serialize());
        db.update(TABLE_NAME, contentValues, ID + " = ?", new String[] { String.valueOf(id) });

        updates.put(RecipientId.from(id), newColor);
      }
    } finally {
      db.setTransactionSuccessful();
      db.endTransaction();

      Stream.of(updates.entrySet()).forEach(entry -> Recipient.live(entry.getKey()).refresh());
    }
  }
  public @Nullable Cursor getSignalContacts() {
    String   selection = BLOCKED         + " = ? AND " +
                         REGISTERED      + " = ? AND " +
                         GROUP_ID        + " IS NULL AND " +
                         "(" + SYSTEM_DISPLAY_NAME + " NOT NULL OR " + SEARCH_PROFILE_NAME + " NOT NULL OR " + USERNAME + " NOT NULL)";
    String[] args      = new String[] { "0", String.valueOf(RegisteredState.REGISTERED.getId()) };
    String   orderBy   = SORT_NAME + ", " + SYSTEM_DISPLAY_NAME + ", " + SEARCH_PROFILE_NAME + ", " + USERNAME + ", " + PHONE;

    return databaseHelper.getReadableDatabase().query(TABLE_NAME, SEARCH_PROJECTION, selection, args, null, null, orderBy);
  }

  public @Nullable Cursor querySignalContacts(@NonNull String query) {
    query = TextUtils.isEmpty(query) ? "*" : query;
    query = "%" + query + "%";

    String   selection = BLOCKED         + " = ? AND " +
                         REGISTERED      + " = ? AND " +
                         GROUP_ID        + " IS NULL AND " +
                         "(" +
                           PHONE               + " LIKE ? OR " +
                           SYSTEM_DISPLAY_NAME + " LIKE ? OR " +
                           SEARCH_PROFILE_NAME + " LIKE ? OR " +
                           USERNAME            + " LIKE ?" +
                         ")";
    String[] args      = new String[] { "0", String.valueOf(RegisteredState.REGISTERED.getId()), query, query, query, query };
    String   orderBy   = SORT_NAME + ", " + SYSTEM_DISPLAY_NAME + ", " + SEARCH_PROFILE_NAME + ", " + PHONE;

    return databaseHelper.getReadableDatabase().query(TABLE_NAME, SEARCH_PROJECTION, selection, args, null, null, orderBy);
  }

  public @Nullable Cursor getNonSignalContacts() {
    String   selection = BLOCKED    + " = ? AND " +
                         REGISTERED + " != ? AND " +
                         GROUP_ID   + " IS NULL AND " +
                         SYSTEM_DISPLAY_NAME + " NOT NULL AND " +
                         "(" + PHONE + " NOT NULL OR " + EMAIL + " NOT NULL)";
    String[] args      = new String[] { "0", String.valueOf(RegisteredState.REGISTERED.getId()) };
    String   orderBy   = SYSTEM_DISPLAY_NAME + ", " + PHONE;

    return databaseHelper.getReadableDatabase().query(TABLE_NAME, SEARCH_PROJECTION, selection, args, null, null, orderBy);
  }

  public @Nullable Cursor queryNonSignalContacts(@NonNull String query) {
    query = TextUtils.isEmpty(query) ? "*" : query;
    query = "%" + query + "%";

    String   selection = BLOCKED    + " = ? AND " +
                         REGISTERED + " != ? AND " +
                         GROUP_ID   + " IS NULL AND " +
                         SYSTEM_DISPLAY_NAME + " NOT NULL AND " +
                         "(" + PHONE + " NOT NULL OR " + EMAIL + " NOT NULL) AND " +
                         "(" +
                           PHONE               + " LIKE ? OR " +
                           EMAIL               + " LIKE ? OR " +
                           SYSTEM_DISPLAY_NAME + " LIKE ?" +
                         ")";
    String[] args      = new String[] { "0", String.valueOf(RegisteredState.REGISTERED.getId()), query, query, query };
    String   orderBy   = SYSTEM_DISPLAY_NAME + ", " + PHONE;

    return databaseHelper.getReadableDatabase().query(TABLE_NAME, SEARCH_PROJECTION, selection, args, null, null, orderBy);
  }

  public @Nullable Cursor queryAllContacts(@NonNull String query) {
    query = TextUtils.isEmpty(query) ? "*" : query;
    query = "%" + query + "%";

    String   selection = BLOCKED + " = ? AND " +
                         "(" +
                           SYSTEM_DISPLAY_NAME + " LIKE ? OR " +
                           SEARCH_PROFILE_NAME + " LIKE ? OR " +
                           PHONE               + " LIKE ? OR " +
                           EMAIL               + " LIKE ?" +
                         ")";
    String[] args      = new String[] { "0", query, query, query, query };

    return databaseHelper.getReadableDatabase().query(TABLE_NAME, SEARCH_PROJECTION, selection, args, null, null, null);
  }

  public @NonNull List<Recipient> getRecipientsForMultiDeviceSync() {
    String   subquery  = "SELECT " + ThreadDatabase.TABLE_NAME + "." + ThreadDatabase.RECIPIENT_ID + " FROM " + ThreadDatabase.TABLE_NAME;
    String   selection = REGISTERED      + " = ? AND " +
                         GROUP_ID        + " IS NULL AND " +
                         ID              + " != ? AND " +
                         "(" + SYSTEM_DISPLAY_NAME + " NOT NULL OR " + ID + " IN (" + subquery + "))";
    String[] args      = new String[] { String.valueOf(RegisteredState.REGISTERED.getId()), Recipient.self().getId().serialize() };

    List<Recipient> recipients = new ArrayList<>();

    try (Cursor cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME, ID_PROJECTION, selection, args, null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        recipients.add(Recipient.resolved(RecipientId.from(cursor.getLong(cursor.getColumnIndexOrThrow(ID)))));
      }
    }

    return recipients;
  }

  public void applyBlockedUpdate(@NonNull List<SignalServiceAddress> blocked, List<byte[]> groupIds) {
    List<String> blockedE164 = Stream.of(blocked)
                                     .filter(b -> b.getNumber().isPresent())
                                     .map(b -> b.getNumber().get())
                                     .toList();
    List<String> blockedUuid = Stream.of(blocked)
                                     .filter(b -> b.getUuid().isPresent())
                                     .map(b -> b.getUuid().get().toString().toLowerCase())
                                     .toList();

    SQLiteDatabase db = databaseHelper.getWritableDatabase();

    db.beginTransaction();
    try {
      ContentValues resetBlocked = new ContentValues();
      resetBlocked.put(BLOCKED, 0);
      db.update(TABLE_NAME, resetBlocked, null, null);

      ContentValues setBlocked = new ContentValues();
      setBlocked.put(BLOCKED, 1);
      setBlocked.put(PROFILE_SHARING, 0);

      for (String e164 : blockedE164) {
        db.update(TABLE_NAME, setBlocked, PHONE + " = ?", new String[] { e164 });
      }

      for (String uuid : blockedUuid) {
        db.update(TABLE_NAME, setBlocked, UUID + " = ?", new String[] { uuid });
      }

      List<GroupId.V1> groupIdStrings = Stream.of(groupIds).map(GroupId::v1orThrow).toList();

      for (GroupId.V1 groupId : groupIdStrings) {
        db.update(TABLE_NAME, setBlocked, GROUP_ID + " = ?", new String[] { groupId.toString() });
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }

    ApplicationDependencies.getRecipientCache().clear();
  }

  public void updateStorageKeys(@NonNull Map<RecipientId, byte[]> keys) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.beginTransaction();

    try {
      for (Map.Entry<RecipientId, byte[]> entry : keys.entrySet()) {
        ContentValues values = new ContentValues();
        values.put(STORAGE_SERVICE_ID, Base64.encodeBytes(entry.getValue()));
        db.update(TABLE_NAME, values, ID_WHERE, new String[] { entry.getKey().serialize() });
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public void clearDirtyState(@NonNull List<RecipientId> recipients) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.beginTransaction();

    try {
      ContentValues values = new ContentValues();
      values.put(DIRTY, DirtyState.CLEAN.getId());

      for (RecipientId id : recipients) {
        db.update(TABLE_NAME, values, ID_WHERE, new String[]{ id.serialize() });
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  void markDirty(@NonNull RecipientId recipientId, @NonNull DirtyState dirtyState) {
    Log.d(TAG, "Attempting to mark " + recipientId + " with dirty state " + dirtyState, new Throwable());

    ContentValues contentValues = new ContentValues(1);
    contentValues.put(DIRTY, dirtyState.getId());

    String   query = ID + " = ? AND (" + UUID + " NOT NULL OR " + PHONE + " NOT NULL OR " + GROUP_ID + " NOT NULL) AND ";
    String[] args  = new String[] { recipientId.serialize(), String.valueOf(dirtyState.id) };

    switch (dirtyState) {
      case INSERT:
        query += "(" + DIRTY + " < ? OR " + DIRTY + " = ?)";
        args   = SqlUtil.appendArg(args, String.valueOf(DirtyState.DELETE.getId()));

        contentValues.put(STORAGE_SERVICE_ID, Base64.encodeBytes(StorageSyncHelper.generateKey()));
        break;
      case DELETE:
        query += "(" + DIRTY + " < ? OR " + DIRTY + " = ?)";
        args   = SqlUtil.appendArg(args, String.valueOf(DirtyState.INSERT.getId()));
        break;
      default:
        query += DIRTY + " < ?";
    }

    databaseHelper.getWritableDatabase().update(TABLE_NAME, contentValues, query, args);
  }

  /**
   * Will update the database with the content values you specified. It will make an intelligent
   * query such that this will only return true if a row was *actually* updated.
   */
  private boolean update(@NonNull RecipientId id, @NonNull ContentValues contentValues) {
    String              selection   = ID + " = ?";
    String[]            args        = new String[]{id.serialize()};
    SqlUtil.UpdateQuery updateQuery = SqlUtil.buildTrueUpdateQuery(selection, args, contentValues);

    return update(updateQuery, contentValues);
  }

  /**
   * Will update the database with the {@param contentValues} you specified.
   * <p>
   * This will only return true if a row was *actually* updated with respect to the where clause of the {@param updateQuery}.
   */
  private boolean update(@NonNull SqlUtil.UpdateQuery updateQuery, @NonNull ContentValues contentValues) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();

    return database.update(TABLE_NAME, contentValues, updateQuery.getWhere(), updateQuery.getWhereArgs()) > 0;
  }

  private @NonNull Optional<RecipientId> getByColumn(@NonNull String column, String value) {
    SQLiteDatabase db    = databaseHelper.getWritableDatabase();
    String         query = column + " = ?";
    String[]       args  = new String[] { value };

    try (Cursor cursor = db.query(TABLE_NAME, ID_PROJECTION, query, args, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        return Optional.of(RecipientId.from(cursor.getLong(cursor.getColumnIndexOrThrow(ID))));
      } else {
        return Optional.absent();
      }
    }
  }

  private @NonNull GetOrInsertResult getOrInsertByColumn(@NonNull String column, String value) {
    if (TextUtils.isEmpty(value)) {
      throw new AssertionError(column + " cannot be empty.");
    }

    Optional<RecipientId> existing = getByColumn(column, value);

    if (existing.isPresent()) {
      return new GetOrInsertResult(existing.get(), false);
    } else {
      ContentValues values = new ContentValues();
      values.put(column, value);

      long id = databaseHelper.getWritableDatabase().insert(TABLE_NAME, null, values);

      if (id < 0) {
        existing = getByColumn(column, value);

        if (existing.isPresent()) {
          return new GetOrInsertResult(existing.get(), false);
        } else {
          throw new AssertionError("Failed to insert recipient!");
        }
      } else {
        return new GetOrInsertResult(RecipientId.from(id), true);
      }
    }
  }

  public class BulkOperationsHandle {

    private final SQLiteDatabase database;

    private final Map<RecipientId, PendingContactInfo> pendingContactInfoMap = new HashMap<>();

    BulkOperationsHandle(SQLiteDatabase database) {
      this.database = database;
    }

    public void setSystemContactInfo(@NonNull RecipientId id,
                                     @Nullable String displayName,
                                     @Nullable String photoUri,
                                     @Nullable String systemPhoneLabel,
                                     int systemPhoneType,
                                     @Nullable String systemContactUri)
    {
      ContentValues dirtyQualifyingValues = new ContentValues();
      dirtyQualifyingValues.put(SYSTEM_DISPLAY_NAME, displayName);

      if (update(id, dirtyQualifyingValues)) {
        markDirty(id, DirtyState.UPDATE);
      }

      ContentValues refreshQualifyingValues = new ContentValues();
      refreshQualifyingValues.put(SYSTEM_PHOTO_URI, photoUri);
      refreshQualifyingValues.put(SYSTEM_PHONE_LABEL, systemPhoneLabel);
      refreshQualifyingValues.put(SYSTEM_PHONE_TYPE, systemPhoneType);
      refreshQualifyingValues.put(SYSTEM_CONTACT_URI, systemContactUri);

      if (update(id, refreshQualifyingValues)) {
        pendingContactInfoMap.put(id, new PendingContactInfo(displayName, photoUri, systemPhoneLabel, systemContactUri));
      }

      ContentValues otherValues = new ContentValues();
      otherValues.put(SYSTEM_INFO_PENDING, 0);
      update(id, otherValues);
    }

    public void finish() {
      markAllRelevantEntriesDirty();
      clearSystemDataForPendingInfo();

      database.setTransactionSuccessful();
      database.endTransaction();

      Stream.of(pendingContactInfoMap.entrySet()).forEach(entry -> Recipient.live(entry.getKey()).refresh());
    }

    private void markAllRelevantEntriesDirty() {
      String   query = SYSTEM_INFO_PENDING + " = ? AND " + STORAGE_SERVICE_ID + " NOT NULL AND " + DIRTY + " < ?";
      String[] args  = new String[] { "1", String.valueOf(DirtyState.UPDATE.getId()) };

      ContentValues values = new ContentValues(1);
      values.put(DIRTY, DirtyState.UPDATE.getId());

      database.update(TABLE_NAME, values, query, args);
    }

    private void clearSystemDataForPendingInfo() {
      String   query = SYSTEM_INFO_PENDING + " = ?";
      String[] args  = new String[] { "1" };

      ContentValues values = new ContentValues(5);

      values.put(SYSTEM_INFO_PENDING, 0);
      values.put(SYSTEM_DISPLAY_NAME, (String) null);
      values.put(SYSTEM_PHOTO_URI, (String) null);
      values.put(SYSTEM_PHONE_LABEL, (String) null);
      values.put(SYSTEM_CONTACT_URI, (String) null);

      database.update(TABLE_NAME, values, query, args);
    }
  }

  private static @NonNull String nullIfEmpty(String column) {
    return "NULLIF(" + column + ", '')";
  }

  public interface ColorUpdater {
    MaterialColor update(@NonNull String name, @Nullable String color);
  }

  public static class RecipientSettings {
    private final RecipientId                     id;
    private final UUID                            uuid;
    private final String                          username;
    private final String                          e164;
    private final String                          email;
    private final GroupId                         groupId;
    private final GroupType                       groupType;
    private final boolean                         blocked;
    private final long                            muteUntil;
    private final VibrateState                    messageVibrateState;
    private final VibrateState                    callVibrateState;
    private final Uri                             messageRingtone;
    private final Uri                             callRingtone;
    private final MaterialColor                   color;
    private final int                             defaultSubscriptionId;
    private final int                             expireMessages;
    private final RegisteredState                 registered;
    private final byte[]                          profileKey;
    private final byte[]                          profileKeyCredential;
    private final String                          systemDisplayName;
    private final String                          systemContactPhoto;
    private final String                          systemPhoneLabel;
    private final String                          systemContactUri;
    private final ProfileName                     signalProfileName;
    private final String                          signalProfileAvatar;
    private final boolean                         hasProfileImage;
    private final boolean                         profileSharing;
    private final String                          notificationChannel;
    private final UnidentifiedAccessMode          unidentifiedAccessMode;
    private final boolean                         forceSmsSelection;
    private final Recipient.Capability            uuidCapability;
    private final Recipient.Capability            groupsV2Capability;
    private final InsightsBannerTier              insightsBannerTier;
    private final byte[]                          storageId;
    private final byte[]                          identityKey;
    private final IdentityDatabase.VerifiedStatus identityStatus;

    RecipientSettings(@NonNull RecipientId id,
                      @Nullable UUID uuid,
                      @Nullable String username,
                      @Nullable String e164,
                      @Nullable String email,
                      @Nullable GroupId groupId,
                      @NonNull GroupType groupType,
                      boolean blocked,
                      long muteUntil,
                      @NonNull VibrateState messageVibrateState,
                      @NonNull VibrateState callVibrateState,
                      @Nullable Uri messageRingtone,
                      @Nullable Uri callRingtone,
                      @Nullable MaterialColor color,
                      int defaultSubscriptionId,
                      int expireMessages,
                      @NonNull  RegisteredState registered,
                      @Nullable byte[] profileKey,
                      @Nullable byte[] profileKeyCredential,
                      @Nullable String systemDisplayName,
                      @Nullable String systemContactPhoto,
                      @Nullable String systemPhoneLabel,
                      @Nullable String systemContactUri,
                      @NonNull ProfileName signalProfileName,
                      @Nullable String signalProfileAvatar,
                      boolean hasProfileImage,
                      boolean profileSharing,
                      @Nullable String notificationChannel,
                      @NonNull UnidentifiedAccessMode unidentifiedAccessMode,
                      boolean forceSmsSelection,
                      Recipient.Capability uuidCapability,
                      Recipient.Capability groupsV2Capability,
                      @NonNull InsightsBannerTier insightsBannerTier,
                      @Nullable byte[] storageId,
                      @Nullable byte[] identityKey,
                      @NonNull IdentityDatabase.VerifiedStatus identityStatus)
    {
      this.id                     = id;
      this.uuid                   = uuid;
      this.username               = username;
      this.e164                   = e164;
      this.email                  = email;
      this.groupId                = groupId;
      this.groupType              = groupType;
      this.blocked                = blocked;
      this.muteUntil              = muteUntil;
      this.messageVibrateState    = messageVibrateState;
      this.callVibrateState       = callVibrateState;
      this.messageRingtone        = messageRingtone;
      this.callRingtone           = callRingtone;
      this.color                  = color;
      this.defaultSubscriptionId  = defaultSubscriptionId;
      this.expireMessages         = expireMessages;
      this.registered             = registered;
      this.profileKey             = profileKey;
      this.profileKeyCredential   = profileKeyCredential;
      this.systemDisplayName      = systemDisplayName;
      this.systemContactPhoto     = systemContactPhoto;
      this.systemPhoneLabel       = systemPhoneLabel;
      this.systemContactUri       = systemContactUri;
      this.signalProfileName      = signalProfileName;
      this.signalProfileAvatar    = signalProfileAvatar;
      this.hasProfileImage        = hasProfileImage;
      this.profileSharing         = profileSharing;
      this.notificationChannel    = notificationChannel;
      this.unidentifiedAccessMode = unidentifiedAccessMode;
      this.forceSmsSelection      = forceSmsSelection;
      this.uuidCapability         = uuidCapability;
      this.groupsV2Capability     = groupsV2Capability;
      this.insightsBannerTier     = insightsBannerTier;
      this.storageId              = storageId;
      this.identityKey            = identityKey;
      this.identityStatus         = identityStatus;
    }

    public RecipientId getId() {
      return id;
    }

    public @Nullable UUID getUuid() {
      return uuid;
    }

    public @Nullable String getUsername() {
      return username;
    }

    public @Nullable String getE164() {
      return e164;
    }

    public @Nullable String getEmail() {
      return email;
    }

    public @Nullable GroupId getGroupId() {
      return groupId;
    }

    public @NonNull GroupType getGroupType() {
      return groupType;
    }

    public @Nullable MaterialColor getColor() {
      return color;
    }

    public boolean isBlocked() {
      return blocked;
    }

    public long getMuteUntil() {
      return muteUntil;
    }

    public @NonNull VibrateState getMessageVibrateState() {
      return messageVibrateState;
    }

    public @NonNull VibrateState getCallVibrateState() {
      return callVibrateState;
    }

    public @Nullable Uri getMessageRingtone() {
      return messageRingtone;
    }

    public @Nullable Uri getCallRingtone() {
      return callRingtone;
    }

    public @NonNull InsightsBannerTier getInsightsBannerTier() {
      return insightsBannerTier;
    }

    public Optional<Integer> getDefaultSubscriptionId() {
      return defaultSubscriptionId != -1 ? Optional.of(defaultSubscriptionId) : Optional.absent();
    }

    public int getExpireMessages() {
      return expireMessages;
    }

    public RegisteredState getRegistered() {
      return registered;
    }

    public @Nullable byte[] getProfileKey() {
      return profileKey;
    }

    public @Nullable byte[] getProfileKeyCredential() {
      return profileKeyCredential;
    }

    public @Nullable String getSystemDisplayName() {
      return systemDisplayName;
    }

    public @Nullable String getSystemContactPhotoUri() {
      return systemContactPhoto;
    }

    public @Nullable String getSystemPhoneLabel() {
      return systemPhoneLabel;
    }

    public @Nullable String getSystemContactUri() {
      return systemContactUri;
    }

    public @NonNull ProfileName getProfileName() {
      return signalProfileName;
    }

    public @Nullable String getProfileAvatar() {
      return signalProfileAvatar;
    }

    public boolean hasProfileImage() {
      return hasProfileImage;
    }

    public boolean isProfileSharing() {
      return profileSharing;
    }

    public @Nullable String getNotificationChannel() {
      return notificationChannel;
    }

    public @NonNull UnidentifiedAccessMode getUnidentifiedAccessMode() {
      return unidentifiedAccessMode;
    }

    public boolean isForceSmsSelection() {
      return forceSmsSelection;
    }

    public Recipient.Capability getUuidCapability() {
      return uuidCapability;
    }

    public Recipient.Capability getGroupsV2Capability() {
      return groupsV2Capability;
    }

    public @Nullable byte[] getStorageId() {
      return storageId;
    }

    public @Nullable byte[] getIdentityKey() {
      return identityKey;
    }

    public @NonNull IdentityDatabase.VerifiedStatus getIdentityStatus() {
      return identityStatus;
    }
  }

  public static class RecipientReader implements Closeable {

    private final Cursor  cursor;

    RecipientReader(Cursor cursor) {
      this.cursor  = cursor;
    }

    public @NonNull Recipient getCurrent() {
      RecipientId id = RecipientId.from(cursor.getLong(cursor.getColumnIndexOrThrow(ID)));
      return Recipient.resolved(id);
    }

    public @Nullable Recipient getNext() {
      if (cursor != null && !cursor.moveToNext()) {
        return null;
      }

      return getCurrent();
    }

    public void close() {
      cursor.close();
    }
  }

  private static class PendingContactInfo {

    private final String displayName;
    private final String photoUri;
    private final String phoneLabel;
    private final String contactUri;

    private PendingContactInfo(String displayName, String photoUri, String phoneLabel, String contactUri) {
      this.displayName = displayName;
      this.photoUri    = photoUri;
      this.phoneLabel  = phoneLabel;
      this.contactUri  = contactUri;
    }
  }

  public static class MissingRecipientError extends AssertionError {
    public MissingRecipientError(@Nullable RecipientId id) {
      super("Failed to find recipient with ID: " + id);
    }
  }

  private static class GetOrInsertResult {
    final RecipientId recipientId;
    final boolean     neededInsert;

    private GetOrInsertResult(@NonNull RecipientId recipientId, boolean neededInsert) {
      this.recipientId  = recipientId;
      this.neededInsert = neededInsert;
    }
  }
}
