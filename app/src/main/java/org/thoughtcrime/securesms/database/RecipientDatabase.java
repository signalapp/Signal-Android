package org.thoughtcrime.securesms.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.annimon.stream.Stream;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import net.sqlcipher.SQLException;
import net.sqlcipher.database.SQLiteConstraintException;

import org.jetbrains.annotations.NotNull;
import org.signal.core.util.logging.Log;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.signal.zkgroup.profiles.ProfileKey;
import org.signal.zkgroup.profiles.ProfileKeyCredential;
import org.thoughtcrime.securesms.color.MaterialColor;
import org.thoughtcrime.securesms.conversation.colors.AvatarColor;
import org.thoughtcrime.securesms.conversation.colors.ChatColors;
import org.thoughtcrime.securesms.conversation.colors.ChatColorsMapper;
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil;
import org.thoughtcrime.securesms.database.IdentityDatabase.IdentityRecord;
import org.thoughtcrime.securesms.database.IdentityDatabase.VerifiedStatus;
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.database.model.databaseprotos.ChatColor;
import org.thoughtcrime.securesms.database.model.databaseprotos.DeviceLastResetTime;
import org.thoughtcrime.securesms.database.model.databaseprotos.ProfileKeyCredentialColumnData;
import org.thoughtcrime.securesms.database.model.databaseprotos.RecipientExtras;
import org.thoughtcrime.securesms.database.model.databaseprotos.Wallpaper;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.v2.ProfileKeySet;
import org.thoughtcrime.securesms.groups.v2.processing.GroupsV2StateProcessor;
import org.thoughtcrime.securesms.jobs.RefreshAttributesJob;
import org.thoughtcrime.securesms.jobs.RequestGroupV2InfoJob;
import org.thoughtcrime.securesms.jobs.RetrieveProfileJob;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.profiles.ProfileName;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.storage.StorageRecordUpdate;
import org.thoughtcrime.securesms.storage.StorageSyncHelper;
import org.thoughtcrime.securesms.storage.StorageSyncModels;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.Bitmask;
import org.thoughtcrime.securesms.util.CursorUtil;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.IdentityUtil;
import org.thoughtcrime.securesms.util.SqlUtil;
import org.thoughtcrime.securesms.util.StringUtil;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.wallpaper.ChatWallpaper;
import org.thoughtcrime.securesms.wallpaper.ChatWallpaperFactory;
import org.thoughtcrime.securesms.wallpaper.WallpaperStorage;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.storage.SignalAccountRecord;
import org.whispersystems.signalservice.api.storage.SignalContactRecord;
import org.whispersystems.signalservice.api.storage.SignalGroupV1Record;
import org.whispersystems.signalservice.api.storage.SignalGroupV2Record;
import org.whispersystems.signalservice.api.storage.StorageId;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class RecipientDatabase extends Database {

  private static final String TAG = Log.tag(RecipientDatabase.class);

          static final String TABLE_NAME                = "recipient";
  public  static final String ID                        = "_id";
  private static final String UUID                      = "uuid";
  private static final String USERNAME                  = "username";
  public  static final String PHONE                     = "phone";
  public  static final String EMAIL                     = "email";
          static final String GROUP_ID                  = "group_id";
          static final String GROUP_TYPE                = "group_type";
  private static final String BLOCKED                   = "blocked";
  private static final String MESSAGE_RINGTONE          = "message_ringtone";
  private static final String MESSAGE_VIBRATE           = "message_vibrate";
  private static final String CALL_RINGTONE             = "call_ringtone";
  private static final String CALL_VIBRATE              = "call_vibrate";
  private static final String NOTIFICATION_CHANNEL      = "notification_channel";
  private static final String MUTE_UNTIL                = "mute_until";
  private static final String AVATAR_COLOR              = "color";
  private static final String SEEN_INVITE_REMINDER      = "seen_invite_reminder";
  private static final String DEFAULT_SUBSCRIPTION_ID   = "default_subscription_id";
  private static final String MESSAGE_EXPIRATION_TIME   = "message_expiration_time";
  public  static final String REGISTERED                = "registered";
  public  static final String SYSTEM_JOINED_NAME        = "system_display_name";
  public  static final String SYSTEM_FAMILY_NAME        = "system_family_name";
  public  static final String SYSTEM_GIVEN_NAME         = "system_given_name";
  private static final String SYSTEM_PHOTO_URI          = "system_photo_uri";
  public  static final String SYSTEM_PHONE_TYPE         = "system_phone_type";
  public  static final String SYSTEM_PHONE_LABEL        = "system_phone_label";
  private static final String SYSTEM_CONTACT_URI        = "system_contact_uri";
  private static final String SYSTEM_INFO_PENDING       = "system_info_pending";
  private static final String PROFILE_KEY               = "profile_key";
  private static final String PROFILE_KEY_CREDENTIAL    = "profile_key_credential";
  private static final String SIGNAL_PROFILE_AVATAR     = "signal_profile_avatar";
  private static final String PROFILE_SHARING           = "profile_sharing";
  private static final String LAST_PROFILE_FETCH        = "last_profile_fetch";
  private static final String UNIDENTIFIED_ACCESS_MODE  = "unidentified_access_mode";
          static final String FORCE_SMS_SELECTION       = "force_sms_selection";
  private static final String CAPABILITIES              = "capabilities";
  private static final String STORAGE_SERVICE_ID        = "storage_service_key";
  private static final String PROFILE_GIVEN_NAME        = "signal_profile_name";
  private static final String PROFILE_FAMILY_NAME       = "profile_family_name";
  private static final String PROFILE_JOINED_NAME       = "profile_joined_name";
  private static final String MENTION_SETTING           = "mention_setting";
  private static final String STORAGE_PROTO             = "storage_proto";
  private static final String LAST_SESSION_RESET        = "last_session_reset";
  private static final String WALLPAPER                 = "wallpaper";
  private static final String WALLPAPER_URI             = "wallpaper_file";
  public static final  String ABOUT                     = "about";
  public static final  String ABOUT_EMOJI               = "about_emoji";
  private static final String EXTRAS                    = "extras";
  private static final String GROUPS_IN_COMMON          = "groups_in_common";
  private static final String CHAT_COLORS               = "chat_colors";
  private static final String CUSTOM_CHAT_COLORS_ID     = "custom_chat_colors_id";

  public  static final String SEARCH_PROFILE_NAME      = "search_signal_profile";
  private static final String SORT_NAME                = "sort_name";
  private static final String IDENTITY_STATUS          = "identity_status";
  private static final String IDENTITY_KEY             = "identity_key";

  /**
   * Values that represent the index in the capabilities bitmask. Each index can store a 2-bit
   * value, which in this case is the value of {@link Recipient.Capability}.
   */
  private static final class Capabilities {
    static final int BIT_LENGTH = 2;

    static final int GROUPS_V2           = 0;
    static final int GROUPS_V1_MIGRATION = 1;
    static final int SENDER_KEY          = 2;
    static final int ANNOUNCEMENT_GROUPS = 3;
  }

  private static final String[] RECIPIENT_PROJECTION = new String[] {
      ID, UUID, USERNAME, PHONE, EMAIL, GROUP_ID, GROUP_TYPE,
      BLOCKED, MESSAGE_RINGTONE, CALL_RINGTONE, MESSAGE_VIBRATE, CALL_VIBRATE, MUTE_UNTIL, AVATAR_COLOR, SEEN_INVITE_REMINDER, DEFAULT_SUBSCRIPTION_ID, MESSAGE_EXPIRATION_TIME, REGISTERED,
      PROFILE_KEY, PROFILE_KEY_CREDENTIAL,
      SYSTEM_JOINED_NAME, SYSTEM_GIVEN_NAME, SYSTEM_FAMILY_NAME, SYSTEM_PHOTO_URI, SYSTEM_PHONE_LABEL, SYSTEM_PHONE_TYPE, SYSTEM_CONTACT_URI,
      PROFILE_GIVEN_NAME, PROFILE_FAMILY_NAME, SIGNAL_PROFILE_AVATAR, PROFILE_SHARING, LAST_PROFILE_FETCH,
      NOTIFICATION_CHANNEL,
      UNIDENTIFIED_ACCESS_MODE,
      FORCE_SMS_SELECTION,
      CAPABILITIES,
      STORAGE_SERVICE_ID,
      MENTION_SETTING, WALLPAPER, WALLPAPER_URI,
      MENTION_SETTING,
      ABOUT, ABOUT_EMOJI,
      EXTRAS, GROUPS_IN_COMMON,
      CHAT_COLORS, CUSTOM_CHAT_COLORS_ID
  };

  private static final String[] ID_PROJECTION              = new String[]{ID};
  private static final String[] SEARCH_PROJECTION          = new String[]{ID, SYSTEM_JOINED_NAME, PHONE, EMAIL, SYSTEM_PHONE_LABEL, SYSTEM_PHONE_TYPE, REGISTERED, ABOUT, ABOUT_EMOJI, EXTRAS, GROUPS_IN_COMMON, "COALESCE(" + nullIfEmpty(PROFILE_JOINED_NAME) + ", " + nullIfEmpty(PROFILE_GIVEN_NAME) + ") AS " + SEARCH_PROFILE_NAME, "LOWER(COALESCE(" + nullIfEmpty(SYSTEM_JOINED_NAME) + ", " + nullIfEmpty(SYSTEM_GIVEN_NAME) + ", " + nullIfEmpty(PROFILE_JOINED_NAME) + ", " + nullIfEmpty(PROFILE_GIVEN_NAME) + ", " + nullIfEmpty(USERNAME) + ")) AS " + SORT_NAME};
  public  static final String[] SEARCH_PROJECTION_NAMES    = new String[]{ID, SYSTEM_JOINED_NAME, PHONE, EMAIL, SYSTEM_PHONE_LABEL, SYSTEM_PHONE_TYPE, REGISTERED, ABOUT, ABOUT_EMOJI, EXTRAS, GROUPS_IN_COMMON, SEARCH_PROFILE_NAME, SORT_NAME};
  private static final String[] TYPED_RECIPIENT_PROJECTION = Stream.of(RECIPIENT_PROJECTION)
                                                                   .map(columnName -> TABLE_NAME + "." + columnName)
                                                                   .toList().toArray(new String[0]);

  static final String[] TYPED_RECIPIENT_PROJECTION_NO_ID = Arrays.copyOfRange(TYPED_RECIPIENT_PROJECTION, 1, TYPED_RECIPIENT_PROJECTION.length);

  private static final String[] MENTION_SEARCH_PROJECTION  = new String[]{ID, removeWhitespace("COALESCE(" + nullIfEmpty(SYSTEM_JOINED_NAME) + ", " + nullIfEmpty(SYSTEM_GIVEN_NAME) + ", " + nullIfEmpty(PROFILE_JOINED_NAME) + ", " + nullIfEmpty(PROFILE_GIVEN_NAME) + ", " + nullIfEmpty(USERNAME) + ", " + nullIfEmpty(PHONE) + ")") + " AS " + SORT_NAME};

  public static final String[] CREATE_INDEXS = new String[] {
      "CREATE INDEX IF NOT EXISTS recipient_group_type_index ON " + TABLE_NAME + " (" + GROUP_TYPE + ");",
  };

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

    public static VibrateState fromBoolean(boolean enabled) {
      return enabled ? ENABLED : DISABLED;
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

  public enum GroupType {
    NONE(0), MMS(1), SIGNAL_V1(2), SIGNAL_V2(3);

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

  public enum MentionSetting {
    ALWAYS_NOTIFY(0), DO_NOT_NOTIFY(1);

    private final int id;

    MentionSetting(int id) {
      this.id = id;
    }

    int getId() {
      return id;
    }

    public static MentionSetting fromId(int id) {
      return values()[id];
    }
  }

  public static final String CREATE_TABLE =
      "CREATE TABLE " + TABLE_NAME + " (" + ID                        + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                            UUID                      + " TEXT UNIQUE DEFAULT NULL, " +
                                            USERNAME                  + " TEXT UNIQUE DEFAULT NULL, " +
                                            PHONE                     + " TEXT UNIQUE DEFAULT NULL, " +
                                            EMAIL                     + " TEXT UNIQUE DEFAULT NULL, " +
                                            GROUP_ID                  + " TEXT UNIQUE DEFAULT NULL, " +
                                            GROUP_TYPE                + " INTEGER DEFAULT " + GroupType.NONE.getId() +  ", " +
                                            BLOCKED                   + " INTEGER DEFAULT 0," +
                                            MESSAGE_RINGTONE          + " TEXT DEFAULT NULL, " +
                                            MESSAGE_VIBRATE           + " INTEGER DEFAULT " + VibrateState.DEFAULT.getId() + ", " +
                                            CALL_RINGTONE             + " TEXT DEFAULT NULL, " +
                                            CALL_VIBRATE              + " INTEGER DEFAULT " + VibrateState.DEFAULT.getId() + ", " +
                                            NOTIFICATION_CHANNEL      + " TEXT DEFAULT NULL, " +
                                            MUTE_UNTIL                + " INTEGER DEFAULT 0, " +
                                            AVATAR_COLOR              + " TEXT DEFAULT NULL, " +
                                            SEEN_INVITE_REMINDER      + " INTEGER DEFAULT " + InsightsBannerTier.NO_TIER.getId() + ", " +
                                            DEFAULT_SUBSCRIPTION_ID   + " INTEGER DEFAULT -1, " +
                                            MESSAGE_EXPIRATION_TIME   + " INTEGER DEFAULT 0, " +
                                            REGISTERED                + " INTEGER DEFAULT " + RegisteredState.UNKNOWN.getId() + ", " +
                                            SYSTEM_GIVEN_NAME         + " TEXT DEFAULT NULL, " +
                                            SYSTEM_FAMILY_NAME        + " TEXT DEFAULT NULL, " +
                                            SYSTEM_JOINED_NAME        + " TEXT DEFAULT NULL, " +
                                            SYSTEM_PHOTO_URI          + " TEXT DEFAULT NULL, " +
                                            SYSTEM_PHONE_LABEL        + " TEXT DEFAULT NULL, " +
                                            SYSTEM_PHONE_TYPE         + " INTEGER DEFAULT -1, " +
                                            SYSTEM_CONTACT_URI        + " TEXT DEFAULT NULL, " +
                                            SYSTEM_INFO_PENDING       + " INTEGER DEFAULT 0, " +
                                            PROFILE_KEY               + " TEXT DEFAULT NULL, " +
                                            PROFILE_KEY_CREDENTIAL    + " TEXT DEFAULT NULL, " +
                                            PROFILE_GIVEN_NAME        + " TEXT DEFAULT NULL, " +
                                            PROFILE_FAMILY_NAME       + " TEXT DEFAULT NULL, " +
                                            PROFILE_JOINED_NAME       + " TEXT DEFAULT NULL, " +
                                            SIGNAL_PROFILE_AVATAR     + " TEXT DEFAULT NULL, " +
                                            PROFILE_SHARING           + " INTEGER DEFAULT 0, " +
                                            LAST_PROFILE_FETCH        + " INTEGER DEFAULT 0, " +
                                            UNIDENTIFIED_ACCESS_MODE  + " INTEGER DEFAULT 0, " +
                                            FORCE_SMS_SELECTION       + " INTEGER DEFAULT 0, " +
                                            STORAGE_SERVICE_ID        + " TEXT UNIQUE DEFAULT NULL, " +
                                            MENTION_SETTING           + " INTEGER DEFAULT " + MentionSetting.ALWAYS_NOTIFY.getId() + ", " +
                                            STORAGE_PROTO             + " TEXT DEFAULT NULL, " +
                                            CAPABILITIES              + " INTEGER DEFAULT 0, " +
                                            LAST_SESSION_RESET        + " BLOB DEFAULT NULL, " +
                                            WALLPAPER                 + " BLOB DEFAULT NULL, " +
                                            WALLPAPER_URI             + " TEXT DEFAULT NULL, " +
                                            ABOUT                     + " TEXT DEFAULT NULL, " +
                                            ABOUT_EMOJI               + " TEXT DEFAULT NULL, " +
                                            EXTRAS                    + " BLOB DEFAULT NULL, " +
                                            GROUPS_IN_COMMON          + " INTEGER DEFAULT 0, " +
                                            CHAT_COLORS               + " BLOB DEFAULT NULL, " +
                                            CUSTOM_CHAT_COLORS_ID     + " INTEGER DEFAULT 0);";

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

  public @NonNull
  Optional<RecipientId> getByE164(@NonNull String e164) {
    return getByColumn(PHONE, e164);
  }

  public @NonNull
  Optional<RecipientId> getByEmail(@NonNull String email) {
    return getByColumn(EMAIL, email);
  }

  public @NonNull Optional<RecipientId> getByGroupId(@NonNull GroupId groupId) {
    return getByColumn(GROUP_ID, groupId.toString());
  }

  public @NonNull
  Optional<RecipientId> getByUuid(@NonNull UUID uuid) {
    return getByColumn(UUID, uuid.toString());
  }

  public @NonNull
  Optional<RecipientId> getByUsername(@NonNull String username) {
    return getByColumn(USERNAME, username);
  }

  public @NonNull RecipientId getAndPossiblyMerge(@Nullable UUID uuid, @Nullable String e164, boolean highTrust) {
    if (uuid == null && e164 == null) {
      throw new IllegalArgumentException("Must provide a UUID or E164!");
    }

    RecipientId                    recipientNeedingRefresh = null;
    Pair<RecipientId, RecipientId> remapped                = null;
    boolean                        transactionSuccessful   = false;

    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.beginTransaction();

    try {
      Optional<RecipientId> byE164 = e164 != null ? getByE164(e164) : Optional.absent();
      Optional<RecipientId> byUuid = uuid != null ? getByUuid(uuid) : Optional.absent();

      RecipientId finalId;

      if (!byE164.isPresent() && !byUuid.isPresent()) {
        Log.i(TAG, "Discovered a completely new user. Inserting.", true);
        if (highTrust) {
          long id = db.insert(TABLE_NAME, null, buildContentValuesForNewUser(e164, uuid));
          finalId = RecipientId.from(id);
        } else {
          long id = db.insert(TABLE_NAME, null, buildContentValuesForNewUser(uuid == null ? e164 : null, uuid));
          finalId = RecipientId.from(id);
        }
      } else if (byE164.isPresent() && !byUuid.isPresent()) {
        if (uuid != null) {
          RecipientSettings e164Settings = getRecipientSettings(byE164.get());
          if (e164Settings.uuid != null) {
            if (highTrust) {
              Log.w(TAG, String.format(Locale.US, "Found out about a UUID (%s) for a known E164 user (%s), but that user already has a UUID (%s). Likely a case of re-registration. High-trust, so stripping the E164 from the existing account and assigning it to a new entry.", uuid, byE164.get(), e164Settings.uuid), true);

              removePhoneNumber(byE164.get(), db);
              recipientNeedingRefresh = byE164.get();

              ContentValues insertValues = buildContentValuesForNewUser(e164, uuid);
              insertValues.put(BLOCKED, e164Settings.blocked ? 1 : 0);

              long id = db.insert(TABLE_NAME, null, insertValues);
              finalId = RecipientId.from(id);
            } else {
              Log.w(TAG, String.format(Locale.US, "Found out about a UUID (%s) for a known E164 user (%s), but that user already has a UUID (%s). Likely a case of re-registration. Low-trust, so making a new user for the UUID.", uuid, byE164.get(), e164Settings.uuid), true);

              long id = db.insert(TABLE_NAME, null, buildContentValuesForNewUser(null, uuid));
              finalId = RecipientId.from(id);
            }
          } else {
            if (highTrust) {
              Log.i(TAG, String.format(Locale.US, "Found out about a UUID (%s) for a known E164 user (%s). High-trust, so updating.", uuid, byE164.get()), true);
              markRegisteredOrThrow(byE164.get(), uuid);
              finalId = byE164.get();
            } else {
              Log.i(TAG, String.format(Locale.US, "Found out about a UUID (%s) for a known E164 user (%s). Low-trust, so making a new user for the UUID.", uuid, byE164.get()), true);
              long id = db.insert(TABLE_NAME, null, buildContentValuesForNewUser(null, uuid));
              finalId = RecipientId.from(id);
            }
          }
        } else {
          finalId = byE164.get();
        }
      } else if (!byE164.isPresent() && byUuid.isPresent()) {
        if (e164 != null) {
          if (highTrust) {
            Log.i(TAG, String.format(Locale.US, "Found out about an E164 (%s) for a known UUID user (%s). High-trust, so updating.", e164, byUuid.get()), true);
            setPhoneNumberOrThrow(byUuid.get(), e164);
            finalId = byUuid.get();
          } else {
            Log.i(TAG, String.format(Locale.US, "Found out about an E164 (%s) for a known UUID user (%s). Low-trust, so doing nothing.", e164, byUuid.get()), true);
            finalId = byUuid.get();
          }
        } else {
          finalId = byUuid.get();
        }
      } else {
        if (byE164.equals(byUuid)) {
          finalId = byUuid.get();
        } else {
          Log.w(TAG, String.format(Locale.US, "Hit a conflict between %s (E164 of %s) and %s (UUID %s). They map to different recipients.", byE164.get(), e164, byUuid.get(), uuid), new Throwable(), true);

          RecipientSettings e164Settings = getRecipientSettings(byE164.get());

          if (e164Settings.getUuid() != null) {
            if (highTrust) {
              Log.w(TAG, "The E164 contact has a different UUID. Likely a case of re-registration. High-trust, so stripping the E164 from the existing account and assigning it to the UUID entry.", true);

              removePhoneNumber(byE164.get(), db);
              recipientNeedingRefresh = byE164.get();

              setPhoneNumberOrThrow(byUuid.get(), Objects.requireNonNull(e164));

              finalId = byUuid.get();
            } else {
              Log.w(TAG, "The E164 contact has a different UUID. Likely a case of re-registration. Low-trust, so doing nothing.", true);
              finalId = byUuid.get();
            }
          } else {
            if (highTrust) {
              Log.w(TAG, "We have one contact with just an E164, and another with UUID. High-trust, so merging the two rows together.", true);
              finalId                 = merge(byUuid.get(), byE164.get());
              recipientNeedingRefresh = byUuid.get();
              remapped                = new Pair<>(byE164.get(), byUuid.get());
            } else {
              Log.w(TAG, "We have one contact with just an E164, and another with UUID. Low-trust, so doing nothing.", true);
              finalId  = byUuid.get();
            }
          }
        }
      }

      db.setTransactionSuccessful();
      transactionSuccessful = true;
      return finalId;
    } finally {
      db.endTransaction();

      if (transactionSuccessful) {
        if (recipientNeedingRefresh != null) {
          Recipient.live(recipientNeedingRefresh).refresh();
          RetrieveProfileJob.enqueue(recipientNeedingRefresh);
        }

        if (remapped != null) {
          Recipient.live(remapped.first()).refresh(remapped.second());
          ApplicationDependencies.getRecipientCache().remap(remapped.first(), remapped.second());
        }

        if (recipientNeedingRefresh != null || remapped != null) {
          StorageSyncHelper.scheduleSyncForDataChange();
          RecipientId.clearCache();
        }
      }
    }
  }

  private static ContentValues buildContentValuesForNewUser(@Nullable String e164, @Nullable UUID uuid) {
    ContentValues values = new ContentValues();

    values.put(PHONE, e164);

    if (uuid != null) {
      values.put(UUID, uuid.toString().toLowerCase());
      values.put(REGISTERED, RegisteredState.REGISTERED.getId());
      values.put(STORAGE_SERVICE_ID, Base64.encodeBytes(StorageSyncHelper.generateKey()));
      values.put(AVATAR_COLOR, AvatarColor.random().serialize());
    }

    return values;
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
    Optional<RecipientId> existing = getByGroupId(groupId);

    if (existing.isPresent()) {
      return existing.get();
    } else if (groupId.isV1() && DatabaseFactory.getGroupDatabase(context).groupExists(groupId.requireV1().deriveV2MigrationGroupId())) {
      throw new GroupDatabase.LegacyGroupInsertException(groupId);
    } else if (groupId.isV2() && DatabaseFactory.getGroupDatabase(context).getGroupV1ByExpectedV2(groupId.requireV2()).isPresent()) {
      throw new GroupDatabase.MissedGroupMigrationInsertException(groupId);
    } else {
      ContentValues values = new ContentValues();
      values.put(GROUP_ID, groupId.toString());
      values.put(AVATAR_COLOR, AvatarColor.random().serialize());

      long id = databaseHelper.getWritableDatabase().insert(TABLE_NAME, null, values);

      if (id < 0) {
        existing = getByColumn(GROUP_ID, groupId.toString());

        if (existing.isPresent()) {
          return existing.get();
        } else if (groupId.isV1() && DatabaseFactory.getGroupDatabase(context).groupExists(groupId.requireV1().deriveV2MigrationGroupId())) {
          throw new GroupDatabase.LegacyGroupInsertException(groupId);
        } else if (groupId.isV2() && DatabaseFactory.getGroupDatabase(context).getGroupV1ByExpectedV2(groupId.requireV2()).isPresent()) {
          throw new GroupDatabase.MissedGroupMigrationInsertException(groupId);
        } else {
          throw new AssertionError("Failed to insert recipient!");
        }
      } else {
        ContentValues groupUpdates = new ContentValues();

        if (groupId.isMms()) {
          groupUpdates.put(GROUP_TYPE, GroupType.MMS.getId());
        } else {
          if (groupId.isV2()) {
            groupUpdates.put(GROUP_TYPE, GroupType.SIGNAL_V2.getId());
          } else {
            groupUpdates.put(GROUP_TYPE, GroupType.SIGNAL_V1.getId());
          }
          groupUpdates.put(STORAGE_SERVICE_ID, Base64.encodeBytes(StorageSyncHelper.generateKey()));
        }

        RecipientId recipientId = RecipientId.from(id);

        update(recipientId, groupUpdates);

        return recipientId;
      }
    }
  }

  /**
   * See {@link Recipient#externalPossiblyMigratedGroup(Context, GroupId)}.
   */
  public @NonNull RecipientId getOrInsertFromPossiblyMigratedGroupId(@NonNull GroupId groupId) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();

    db.beginTransaction();
    try {
      Optional<RecipientId> existing = getByColumn(GROUP_ID, groupId.toString());

      if (existing.isPresent()) {
        db.setTransactionSuccessful();
        return existing.get();
      }

      if (groupId.isV1()) {
        Optional<RecipientId> v2 = getByGroupId(groupId.requireV1().deriveV2MigrationGroupId());
        if (v2.isPresent()) {
          db.setTransactionSuccessful();
          return v2.get();
        }
      }

      if (groupId.isV2()) {
        Optional<GroupDatabase.GroupRecord> v1 = DatabaseFactory.getGroupDatabase(context).getGroupV1ByExpectedV2(groupId.requireV2());
        if (v1.isPresent()) {
          db.setTransactionSuccessful();
          return v1.get().getRecipientId();
        }
      }

      RecipientId id = getOrInsertFromGroupId(groupId);

      db.setTransactionSuccessful();
      return id;
    } finally {
      db.endTransaction();
    }
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
    String         query    = ID + " = ?";
    String[]       args     = new String[] { id.serialize() };

    try (Cursor cursor = database.query(TABLE_NAME, RECIPIENT_PROJECTION, query, args, null, null, null)) {
      if (cursor != null && cursor.moveToNext()) {
        return getRecipientSettings(context, cursor);
      } else {
        Optional<RecipientId> remapped = RemappedRecords.getInstance().getRecipient(context, id);
        if (remapped.isPresent()) {
          Log.w(TAG, "Missing recipient for " + id + ", but found it in the remapped records as " + remapped.get());
          return getRecipientSettings(remapped.get());
        } else {
          throw new MissingRecipientException(id);
        }
      }
    }
  }

  public @Nullable RecipientSettings getRecipientSettingsForSync(@NonNull RecipientId id) {
    String   query = TABLE_NAME + "." + ID + " = ?";
    String[] args  = new String[]{id.serialize()};

    List<RecipientSettings> recipientSettingsForSync = getRecipientSettingsForSync(query, args);

    if (recipientSettingsForSync.isEmpty()) {
      return null;
    }

    if (recipientSettingsForSync.size() > 1) {
      throw new AssertionError();
    }

    return recipientSettingsForSync.get(0);
  }

  public @Nullable RecipientSettings getByStorageId(@NonNull byte[] storageId) {
    List<RecipientSettings> result = getRecipientSettingsForSync(TABLE_NAME + "." + STORAGE_SERVICE_ID + " = ?", new String[] { Base64.encodeBytes(storageId) });

    if (result.size() > 0) {
      return result.get(0);
    }

    return null;
  }

  public void markNeedsSyncWithoutRefresh(@NonNull Collection<RecipientId> recipientIds) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();

    db.beginTransaction();
    try {
      for (RecipientId recipientId : recipientIds) {
        rotateStorageId(recipientId);
      }
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public void markNeedsSync(@NonNull RecipientId recipientId) {
    rotateStorageId(recipientId);
    Recipient.live(recipientId).refresh();
  }

  public void applyStorageIdUpdates(@NonNull Map<RecipientId, StorageId> storageIds) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();

    db.beginTransaction();
    try {
      String query = ID + " = ?";

      for (Map.Entry<RecipientId, StorageId> entry : storageIds.entrySet()) {
        ContentValues values = new ContentValues();
        values.put(STORAGE_SERVICE_ID, Base64.encodeBytes(entry.getValue().getRaw()));

        db.update(TABLE_NAME, values, query, new String[] { entry.getKey().serialize() });
      }
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }

    for (RecipientId id : storageIds.keySet()) {
      Recipient.live(id).refresh();
    }
  }

  public void applyStorageSyncContactInsert(@NonNull SignalContactRecord insert) {
    SQLiteDatabase   db               = databaseHelper.getWritableDatabase();
    ThreadDatabase   threadDatabase   = DatabaseFactory.getThreadDatabase(context);

    ContentValues values      = getValuesForStorageContact(insert, true);
    long          id          = db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_IGNORE);
    RecipientId   recipientId = null;

    if (id < 0) {
      Log.w(TAG,  "[applyStorageSyncContactInsert] Failed to insert. Possibly merging.");
      recipientId = getAndPossiblyMerge(insert.getAddress().getUuid().get(), insert.getAddress().getNumber().get(), true);
      db.update(TABLE_NAME, values, ID_WHERE, SqlUtil.buildArgs(recipientId));
    } else {
      recipientId = RecipientId.from(id);
    }

    if (insert.getIdentityKey().isPresent()) {
      try {
        IdentityKey identityKey = new IdentityKey(insert.getIdentityKey().get(), 0);

        DatabaseFactory.getIdentityDatabase(context).updateIdentityAfterSync(recipientId, identityKey, StorageSyncModels.remoteToLocalIdentityStatus(insert.getIdentityState()));
      } catch (InvalidKeyException e) {
        Log.w(TAG, "Failed to process identity key during insert! Skipping.", e);
      }
    }

    threadDatabase.applyStorageSyncUpdate(recipientId, insert);
  }

  public void applyStorageSyncContactUpdate(@NonNull StorageRecordUpdate<SignalContactRecord> update) {
    SQLiteDatabase   db               = databaseHelper.getWritableDatabase();
    IdentityDatabase identityDatabase = DatabaseFactory.getIdentityDatabase(context);
    ContentValues    values           = getValuesForStorageContact(update.getNew(), false);

    try {
      int updateCount = db.update(TABLE_NAME, values, STORAGE_SERVICE_ID + " = ?", new String[]{Base64.encodeBytes(update.getOld().getId().getRaw())});
      if (updateCount < 1) {
        throw new AssertionError("Had an update, but it didn't match any rows!");
      }
    } catch (SQLiteConstraintException e) {
      Log.w(TAG,  "[applyStorageSyncContactUpdate] Failed to update a user by storageId.");

      RecipientId recipientId = getByColumn(STORAGE_SERVICE_ID, Base64.encodeBytes(update.getOld().getId().getRaw())).get();
      Log.w(TAG,  "[applyStorageSyncContactUpdate] Found user " + recipientId + ". Possibly merging.");

      recipientId = getAndPossiblyMerge(update.getNew().getAddress().getUuid().orNull(), update.getNew().getAddress().getNumber().orNull(), true);
      Log.w(TAG,  "[applyStorageSyncContactUpdate] Merged into " + recipientId);

      db.update(TABLE_NAME, values, ID_WHERE, SqlUtil.buildArgs(recipientId));
    }

    RecipientId recipientId = getByStorageKeyOrThrow(update.getNew().getId().getRaw());

    if (StorageSyncHelper.profileKeyChanged(update)) {
      ContentValues clearValues = new ContentValues(1);
      clearValues.putNull(PROFILE_KEY_CREDENTIAL);
      db.update(TABLE_NAME, clearValues, ID_WHERE, SqlUtil.buildArgs(recipientId));
    }

    try {
      Optional<IdentityRecord> oldIdentityRecord = identityDatabase.getIdentity(recipientId);

      if (update.getNew().getIdentityKey().isPresent()) {
        IdentityKey identityKey = new IdentityKey(update.getNew().getIdentityKey().get(), 0);
        DatabaseFactory.getIdentityDatabase(context).updateIdentityAfterSync(recipientId, identityKey, StorageSyncModels.remoteToLocalIdentityStatus(update.getNew().getIdentityState()));
      }

      Optional<IdentityRecord> newIdentityRecord = identityDatabase.getIdentity(recipientId);

      if ((newIdentityRecord.isPresent() && newIdentityRecord.get().getVerifiedStatus() == VerifiedStatus.VERIFIED) &&
          (!oldIdentityRecord.isPresent() || oldIdentityRecord.get().getVerifiedStatus() != VerifiedStatus.VERIFIED))
      {
        IdentityUtil.markIdentityVerified(context, Recipient.resolved(recipientId), true, true);
      } else if ((newIdentityRecord.isPresent() && newIdentityRecord.get().getVerifiedStatus() != VerifiedStatus.VERIFIED) &&
          (oldIdentityRecord.isPresent() && oldIdentityRecord.get().getVerifiedStatus() == VerifiedStatus.VERIFIED))
      {
        IdentityUtil.markIdentityVerified(context, Recipient.resolved(recipientId), false, true);
      }
    } catch (InvalidKeyException e) {
      Log.w(TAG, "Failed to process identity key during update! Skipping.", e);
    }

    DatabaseFactory.getThreadDatabase(context).applyStorageSyncUpdate(recipientId, update.getNew());

    Recipient.live(recipientId).refresh();
  }

  public void applyStorageSyncGroupV1Insert(@NonNull SignalGroupV1Record insert) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();

    long        id          = db.insertOrThrow(TABLE_NAME, null, getValuesForStorageGroupV1(insert, true));
    RecipientId recipientId = RecipientId.from(id);

    DatabaseFactory.getThreadDatabase(context).applyStorageSyncUpdate(recipientId, insert);

    Recipient.live(recipientId).refresh();
  }

  public void applyStorageSyncGroupV1Update(@NonNull StorageRecordUpdate<SignalGroupV1Record> update) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();

    ContentValues values      = getValuesForStorageGroupV1(update.getNew(), false);
    int           updateCount = db.update(TABLE_NAME, values, STORAGE_SERVICE_ID + " = ?", new String[]{Base64.encodeBytes(update.getOld().getId().getRaw())});

    if (updateCount < 1) {
      throw new AssertionError("Had an update, but it didn't match any rows!");
    }

    Recipient recipient = Recipient.externalGroupExact(context, GroupId.v1orThrow(update.getOld().getGroupId()));

    DatabaseFactory.getThreadDatabase(context).applyStorageSyncUpdate(recipient.getId(), update.getNew());

    recipient.live().refresh();
  }

  public void applyStorageSyncGroupV2Insert(@NonNull SignalGroupV2Record insert) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();

    GroupMasterKey masterKey = insert.getMasterKeyOrThrow();
    GroupId.V2     groupId   = GroupId.v2(masterKey);
    ContentValues  values    = getValuesForStorageGroupV2(insert, true);
    long           id        = db.insertOrThrow(TABLE_NAME, null, values);
    Recipient      recipient = Recipient.externalGroupExact(context, groupId);

    Log.i(TAG, "Creating restore placeholder for " + groupId);
    DatabaseFactory.getGroupDatabase(context)
                   .create(masterKey,
                           DecryptedGroup.newBuilder()
                                         .setRevision(GroupsV2StateProcessor.RESTORE_PLACEHOLDER_REVISION)
                                         .build());

    Log.i(TAG, "Scheduling request for latest group info for " + groupId);

    ApplicationDependencies.getJobManager().add(new RequestGroupV2InfoJob(groupId));

    DatabaseFactory.getThreadDatabase(context).applyStorageSyncUpdate(recipient.getId(), insert);

    recipient.live().refresh();
  }

  public void applyStorageSyncGroupV2Update(@NonNull StorageRecordUpdate<SignalGroupV2Record> update) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();

    ContentValues values      = getValuesForStorageGroupV2(update.getNew(), false);
    int           updateCount = db.update(TABLE_NAME, values, STORAGE_SERVICE_ID + " = ?", new String[]{Base64.encodeBytes(update.getOld().getId().getRaw())});

    if (updateCount < 1) {
      throw new AssertionError("Had an update, but it didn't match any rows!");
    }

    GroupMasterKey masterKey = update.getOld().getMasterKeyOrThrow();
    Recipient      recipient = Recipient.externalGroupExact(context, GroupId.v2(masterKey));

    DatabaseFactory.getThreadDatabase(context).applyStorageSyncUpdate(recipient.getId(), update.getNew());

    recipient.live().refresh();
  }

  public void applyStorageSyncAccountUpdate(@NonNull StorageRecordUpdate<SignalAccountRecord> update) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();

    ContentValues        values      = new ContentValues();
    ProfileName          profileName = ProfileName.fromParts(update.getNew().getGivenName().orNull(), update.getNew().getFamilyName().orNull());
    Optional<ProfileKey> localKey    = ProfileKeyUtil.profileKeyOptional(update.getOld().getProfileKey().orNull());
    Optional<ProfileKey> remoteKey   = ProfileKeyUtil.profileKeyOptional(update.getNew().getProfileKey().orNull());
    String               profileKey  = remoteKey.or(localKey).transform(ProfileKey::serialize).transform(Base64::encodeBytes).orNull();

    if (!remoteKey.isPresent()) {
      Log.w(TAG, "Got an empty profile key while applying an account record update!");
    }

    values.put(PROFILE_GIVEN_NAME, profileName.getGivenName());
    values.put(PROFILE_FAMILY_NAME, profileName.getFamilyName());
    values.put(PROFILE_JOINED_NAME, profileName.toString());
    values.put(PROFILE_KEY, profileKey);
    values.put(STORAGE_SERVICE_ID, Base64.encodeBytes(update.getNew().getId().getRaw()));

    if (update.getNew().hasUnknownFields()) {
      values.put(STORAGE_PROTO, Base64.encodeBytes(Objects.requireNonNull(update.getNew().serializeUnknownFields())));
    } else {
      values.putNull(STORAGE_PROTO);
    }

    int updateCount = db.update(TABLE_NAME, values, STORAGE_SERVICE_ID + " = ?", new String[]{Base64.encodeBytes(update.getOld().getId().getRaw())});
    if (updateCount < 1) {
      throw new AssertionError("Account update didn't match any rows!");
    }

    if (!remoteKey.equals(localKey)) {
      ApplicationDependencies.getJobManager().add(new RefreshAttributesJob());
    }

    DatabaseFactory.getThreadDatabase(context).applyStorageSyncUpdate(Recipient.self().getId(), update.getNew());

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

  private static @NonNull ContentValues getValuesForStorageContact(@NonNull SignalContactRecord contact, boolean isInsert) {
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
    values.put(MUTE_UNTIL, contact.getMuteUntil());
    values.put(STORAGE_SERVICE_ID, Base64.encodeBytes(contact.getId().getRaw()));

    if (contact.hasUnknownFields()) {
      values.put(STORAGE_PROTO, Base64.encodeBytes(contact.serializeUnknownFields()));
    } else {
      values.putNull(STORAGE_PROTO);
    }

    if (isInsert) {
      values.put(AVATAR_COLOR, AvatarColor.random().serialize());
    }

    return values;
  }

  private static @NonNull ContentValues getValuesForStorageGroupV1(@NonNull SignalGroupV1Record groupV1, boolean isInsert) {
    ContentValues values = new ContentValues();
    values.put(GROUP_ID, GroupId.v1orThrow(groupV1.getGroupId()).toString());
    values.put(GROUP_TYPE, GroupType.SIGNAL_V1.getId());
    values.put(PROFILE_SHARING, groupV1.isProfileSharingEnabled() ? "1" : "0");
    values.put(BLOCKED, groupV1.isBlocked() ? "1" : "0");
    values.put(MUTE_UNTIL, groupV1.getMuteUntil());
    values.put(STORAGE_SERVICE_ID, Base64.encodeBytes(groupV1.getId().getRaw()));

    if (groupV1.hasUnknownFields()) {
      values.put(STORAGE_PROTO, Base64.encodeBytes(groupV1.serializeUnknownFields()));
    } else {
      values.putNull(STORAGE_PROTO);
    }

    if (isInsert) {
      values.put(AVATAR_COLOR, AvatarColor.random().serialize());
    }

    return values;
  }
  
  private static @NonNull ContentValues getValuesForStorageGroupV2(@NonNull SignalGroupV2Record groupV2, boolean isInsert) {
    ContentValues values = new ContentValues();
    values.put(GROUP_ID, GroupId.v2(groupV2.getMasterKeyOrThrow()).toString());
    values.put(GROUP_TYPE, GroupType.SIGNAL_V2.getId());
    values.put(PROFILE_SHARING, groupV2.isProfileSharingEnabled() ? "1" : "0");
    values.put(BLOCKED, groupV2.isBlocked() ? "1" : "0");
    values.put(MUTE_UNTIL, groupV2.getMuteUntil());
    values.put(STORAGE_SERVICE_ID, Base64.encodeBytes(groupV2.getId().getRaw()));

    if (groupV2.hasUnknownFields()) {
      values.put(STORAGE_PROTO, Base64.encodeBytes(groupV2.serializeUnknownFields()));
    } else {
      values.putNull(STORAGE_PROTO);
    }

    if (isInsert) {
      values.put(AVATAR_COLOR, AvatarColor.random().serialize());
    }

    return values;
  }

  private List<RecipientSettings> getRecipientSettingsForSync(@Nullable String query, @Nullable String[] args) {
    SQLiteDatabase          db    = databaseHelper.getReadableDatabase();
    String                  table = TABLE_NAME + " LEFT OUTER JOIN " + IdentityDatabase.TABLE_NAME + " ON " + TABLE_NAME + "." + ID       + " = " + IdentityDatabase.TABLE_NAME + "." + IdentityDatabase.RECIPIENT_ID
                                               + " LEFT OUTER JOIN " + GroupDatabase.TABLE_NAME    + " ON " + TABLE_NAME + "." + GROUP_ID + " = " + GroupDatabase.TABLE_NAME + "." + GroupDatabase.GROUP_ID
                                               + " LEFT OUTER JOIN " + ThreadDatabase.TABLE_NAME   + " ON " + TABLE_NAME + "." + ID       + " = " + ThreadDatabase.TABLE_NAME + "." + ThreadDatabase.RECIPIENT_ID;
    List<RecipientSettings> out   = new ArrayList<>();

    String[] columns = Stream.of(TYPED_RECIPIENT_PROJECTION,
                                 new String[]{ RecipientDatabase.TABLE_NAME + "." + STORAGE_PROTO,
                                               GroupDatabase.TABLE_NAME     + "." + GroupDatabase.V2_MASTER_KEY,
                                               ThreadDatabase.TABLE_NAME    + "." + ThreadDatabase.ARCHIVED,
                                               ThreadDatabase.TABLE_NAME    + "." + ThreadDatabase.READ,
                                               IdentityDatabase.TABLE_NAME  + "." + IdentityDatabase.VERIFIED + " AS " + IDENTITY_STATUS,
                                               IdentityDatabase.TABLE_NAME  + "." + IdentityDatabase.IDENTITY_KEY + " AS " + IDENTITY_KEY })
                             .flatMap(Stream::of)
                             .toArray(String[]::new);

    try (Cursor cursor = db.query(table, columns, query, args, TABLE_NAME + "." + ID, null, null)) {
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
    String                      query = STORAGE_SERVICE_ID + " NOT NULL AND " + ID + " != ? AND " + GROUP_TYPE + " != ?";
    String[]                    args  = SqlUtil.buildArgs(Recipient.self().getId(), String.valueOf(GroupType.SIGNAL_V2.getId()));
    Map<RecipientId, StorageId> out   = new HashMap<>();

    try (Cursor cursor = db.query(TABLE_NAME, new String[] { ID, STORAGE_SERVICE_ID, GROUP_TYPE }, query, args, null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        RecipientId id         = RecipientId.from(cursor.getLong(cursor.getColumnIndexOrThrow(ID)));
        String      encodedKey = cursor.getString(cursor.getColumnIndexOrThrow(STORAGE_SERVICE_ID));
        GroupType   groupType  = GroupType.fromId(cursor.getInt(cursor.getColumnIndexOrThrow(GROUP_TYPE)));
        byte[]      key        = Base64.decodeOrThrow(encodedKey);

        switch (groupType) {
          case NONE      : out.put(id, StorageId.forContact(key)); break;
          case SIGNAL_V1 : out.put(id, StorageId.forGroupV1(key)); break;
          default        : throw new AssertionError();
        }
      }
    }

    for (GroupId.V2 id : DatabaseFactory.getGroupDatabase(context).getAllGroupV2Ids()) {
      Recipient         recipient                = Recipient.externalGroupExact(context, id);
      RecipientId       recipientId              = recipient.getId();
      RecipientSettings recipientSettingsForSync = getRecipientSettingsForSync(recipientId);

      if (recipientSettingsForSync == null) {
        throw new AssertionError();
      }

      byte[] key = recipientSettingsForSync.storageId;

      if (key == null) {
        throw new AssertionError();
      }

      out.put(recipientId, StorageId.forGroupV2(key));
    }

    return out;
  }

  static @NonNull RecipientSettings getRecipientSettings(@NonNull Context context, @NonNull Cursor cursor) {
    return getRecipientSettings(context, cursor, ID);
  }

  static @NonNull RecipientSettings getRecipientSettings(@NonNull Context context, @NonNull Cursor cursor, @NonNull String idColumnName) {
    long    id                         = CursorUtil.requireLong(cursor, idColumnName);
    UUID    uuid                       = UuidUtil.parseOrNull(CursorUtil.requireString(cursor, UUID));
    String  username                   = CursorUtil.requireString(cursor, USERNAME);
    String  e164                       = CursorUtil.requireString(cursor, PHONE);
    String  email                      = CursorUtil.requireString(cursor, EMAIL);
    GroupId groupId                    = GroupId.parseNullableOrThrow(CursorUtil.requireString(cursor, GROUP_ID));
    int     groupType                  = CursorUtil.requireInt(cursor, GROUP_TYPE);
    boolean blocked                    = CursorUtil.requireBoolean(cursor, BLOCKED);
    String  messageRingtone            = CursorUtil.requireString(cursor, MESSAGE_RINGTONE);
    String  callRingtone               = CursorUtil.requireString(cursor, CALL_RINGTONE);
    int     messageVibrateState        = CursorUtil.requireInt(cursor, MESSAGE_VIBRATE);
    int     callVibrateState           = CursorUtil.requireInt(cursor, CALL_VIBRATE);
    long    muteUntil                  = cursor.getLong(cursor.getColumnIndexOrThrow(MUTE_UNTIL));
    int     insightsBannerTier         = CursorUtil.requireInt(cursor, SEEN_INVITE_REMINDER);
    int     defaultSubscriptionId      = CursorUtil.requireInt(cursor, DEFAULT_SUBSCRIPTION_ID);
    int     expireMessages             = CursorUtil.requireInt(cursor, MESSAGE_EXPIRATION_TIME);
    int     registeredState            = CursorUtil.requireInt(cursor, REGISTERED);
    String  profileKeyString           = CursorUtil.requireString(cursor, PROFILE_KEY);
    String  profileKeyCredentialString = CursorUtil.requireString(cursor, PROFILE_KEY_CREDENTIAL);
    String  systemGivenName            = CursorUtil.requireString(cursor, SYSTEM_GIVEN_NAME);
    String  systemFamilyName           = CursorUtil.requireString(cursor, SYSTEM_FAMILY_NAME);
    String  systemDisplayName          = CursorUtil.requireString(cursor, SYSTEM_JOINED_NAME);
    String  systemContactPhoto         = CursorUtil.requireString(cursor, SYSTEM_PHOTO_URI);
    String  systemPhoneLabel           = CursorUtil.requireString(cursor, SYSTEM_PHONE_LABEL);
    String  systemContactUri           = CursorUtil.requireString(cursor, SYSTEM_CONTACT_URI);
    String  profileGivenName           = CursorUtil.requireString(cursor, PROFILE_GIVEN_NAME);
    String  profileFamilyName          = CursorUtil.requireString(cursor, PROFILE_FAMILY_NAME);
    String  signalProfileAvatar        = CursorUtil.requireString(cursor, SIGNAL_PROFILE_AVATAR);
    boolean profileSharing             = CursorUtil.requireBoolean(cursor, PROFILE_SHARING);
    long    lastProfileFetch           = cursor.getLong(cursor.getColumnIndexOrThrow(LAST_PROFILE_FETCH));
    String  notificationChannel        = CursorUtil.requireString(cursor, NOTIFICATION_CHANNEL);
    int     unidentifiedAccessMode     = CursorUtil.requireInt(cursor, UNIDENTIFIED_ACCESS_MODE);
    boolean forceSmsSelection          = CursorUtil.requireBoolean(cursor, FORCE_SMS_SELECTION);
    long    capabilities               = CursorUtil.requireLong(cursor, CAPABILITIES);
    String  storageKeyRaw              = CursorUtil.requireString(cursor, STORAGE_SERVICE_ID);
    int     mentionSettingId           = CursorUtil.requireInt(cursor, MENTION_SETTING);
    byte[]  wallpaper                  = CursorUtil.requireBlob(cursor, WALLPAPER);
    byte[]  serializedChatColors       = CursorUtil.requireBlob(cursor, CHAT_COLORS);
    long    customChatColorsId         = CursorUtil.requireLong(cursor, CUSTOM_CHAT_COLORS_ID);
    String  serializedAvatarColor      = CursorUtil.requireString(cursor, AVATAR_COLOR);
    String  about                      = CursorUtil.requireString(cursor, ABOUT);
    String  aboutEmoji                 = CursorUtil.requireString(cursor, ABOUT_EMOJI);
    boolean hasGroupsInCommon          = CursorUtil.requireBoolean(cursor, GROUPS_IN_COMMON);

    byte[]               profileKey           = null;
    ProfileKeyCredential profileKeyCredential = null;

    if (profileKeyString != null) {
      try {
        profileKey = Base64.decode(profileKeyString);
      } catch (IOException e) {
        Log.w(TAG, e);
        profileKey = null;
      }

      if (profileKeyCredentialString != null) {
        try {
          byte[] columnDataBytes = Base64.decode(profileKeyCredentialString);

          ProfileKeyCredentialColumnData columnData = ProfileKeyCredentialColumnData.parseFrom(columnDataBytes);

          if (Arrays.equals(columnData.getProfileKey().toByteArray(), profileKey)) {
            profileKeyCredential = new ProfileKeyCredential(columnData.getProfileKeyCredential().toByteArray());
          } else {
            Log.i(TAG, "Out of date profile key credential data ignored on read");
          }
        } catch (InvalidInputException | IOException e) {
          Log.w(TAG, "Profile key credential column data could not be read", e);
        }
      }
    }

    byte[] storageKey = storageKeyRaw != null ? Base64.decodeOrThrow(storageKeyRaw) : null;

    ChatWallpaper chatWallpaper = null;

    if (wallpaper != null) {
      try {
        chatWallpaper = ChatWallpaperFactory.create(Wallpaper.parseFrom(wallpaper));
      } catch (InvalidProtocolBufferException e) {
        Log.w(TAG, "Failed to parse wallpaper.", e);
      }
    }

    ChatColors chatColors = null;
    if (serializedChatColors != null) {
      try {
        chatColors = ChatColors.forChatColor(ChatColors.Id.forLongValue(customChatColorsId), ChatColor.parseFrom(serializedChatColors));
      } catch (InvalidProtocolBufferException e) {
        Log.w(TAG, "Failed to parse chat colors.", e);
      }
    }

    return new RecipientSettings(RecipientId.from(id),
                                 uuid,
                                 username,
                                 e164,
                                 email,
                                 groupId,
                                 GroupType.fromId(groupType),
                                 blocked,
                                 muteUntil,
                                 VibrateState.fromId(messageVibrateState),
                                 VibrateState.fromId(callVibrateState),
                                 Util.uri(messageRingtone),
                                 Util.uri(callRingtone),
                                 defaultSubscriptionId,
                                 expireMessages,
                                 RegisteredState.fromId(registeredState),
                                 profileKey,
                                 profileKeyCredential,
                                 ProfileName.fromParts(systemGivenName, systemFamilyName),
                                 systemDisplayName,
                                 systemContactPhoto,
                                 systemPhoneLabel,
                                 systemContactUri,
                                 ProfileName.fromParts(profileGivenName, profileFamilyName),
                                 signalProfileAvatar,
                                 AvatarHelper.hasAvatar(context, RecipientId.from(id)),
                                 profileSharing,
                                 lastProfileFetch,
                                 notificationChannel,
                                 UnidentifiedAccessMode.fromMode(unidentifiedAccessMode),
                                 forceSmsSelection,
                                 capabilities,
                                 InsightsBannerTier.fromId(insightsBannerTier),
                                 storageKey,
                                 MentionSetting.fromId(mentionSettingId),
                                 chatWallpaper,
                                 chatColors,
                                 AvatarColor.deserialize(serializedAvatarColor),
                                 about,
                                 aboutEmoji,
                                 getSyncExtras(cursor),
                                 getExtras(cursor),
                                 hasGroupsInCommon);
  }

  private static @NonNull RecipientSettings.SyncExtras getSyncExtras(@NonNull Cursor cursor) {
    String         storageProtoRaw = CursorUtil.getString(cursor, STORAGE_PROTO).orNull();
    byte[]         storageProto    = storageProtoRaw != null ? Base64.decodeOrThrow(storageProtoRaw) : null;
    boolean        archived        = CursorUtil.getBoolean(cursor, ThreadDatabase.ARCHIVED).or(false);
    boolean        forcedUnread    = CursorUtil.getInt(cursor, ThreadDatabase.READ).transform(status -> status == ThreadDatabase.ReadStatus.FORCED_UNREAD.serialize()).or(false);
    GroupMasterKey groupMasterKey  = CursorUtil.getBlob(cursor, GroupDatabase.V2_MASTER_KEY).transform(GroupUtil::requireMasterKey).orNull();
    byte[]         identityKey     = CursorUtil.getString(cursor, IDENTITY_KEY).transform(Base64::decodeOrThrow).orNull();
    VerifiedStatus identityStatus  = CursorUtil.getInt(cursor, IDENTITY_STATUS).transform(VerifiedStatus::forState).or(VerifiedStatus.DEFAULT);


    return new RecipientSettings.SyncExtras(storageProto, groupMasterKey, identityKey, identityStatus, archived, forcedUnread);
  }

  private static @Nullable Recipient.Extras getExtras(@NonNull Cursor cursor) {
    return Recipient.Extras.from(getRecipientExtras(cursor));
  }

  private static @Nullable RecipientExtras getRecipientExtras(@NonNull Cursor cursor) {
    final Optional<byte[]> blob = CursorUtil.getBlob(cursor, EXTRAS);

    return blob.transform(b -> {
      try {
        return RecipientExtras.parseFrom(b);
      } catch (InvalidProtocolBufferException e) {
        Log.w(TAG, e);
        throw new AssertionError(e);
      }
    }).orNull();
  }

  public BulkOperationsHandle beginBulkSystemContactUpdate() {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    database.beginTransaction();

    ContentValues contentValues = new ContentValues(1);
    contentValues.put(SYSTEM_INFO_PENDING, 1);

    database.update(TABLE_NAME, contentValues, SYSTEM_CONTACT_URI + " NOT NULL", null);

    return new BulkOperationsHandle(database);
  }

  void onUpdatedChatColors(@NonNull ChatColors chatColors) {
    SQLiteDatabase    database = databaseHelper.getWritableDatabase();
    String            where    = CUSTOM_CHAT_COLORS_ID + " = ?";
    String[]          args     = SqlUtil.buildArgs(chatColors.getId().getLongValue());
    List<RecipientId> updated  = new LinkedList<>();

    try (Cursor cursor = database.query(TABLE_NAME, SqlUtil.buildArgs(ID), where, args, null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        updated.add(RecipientId.from(CursorUtil.requireLong(cursor, ID)));
      }
    }

    if (updated.isEmpty()) {
      Log.d(TAG, "No recipients utilizing updated chat color.");
    } else {
      ContentValues values = new ContentValues(2);

      values.put(CHAT_COLORS, chatColors.serialize().toByteArray());
      values.put(CUSTOM_CHAT_COLORS_ID, chatColors.getId().getLongValue());

      database.update(TABLE_NAME, values, where, args);

      for (RecipientId recipientId : updated) {
        Recipient.live(recipientId).refresh();
      }
    }
  }

  void onDeletedChatColors(@NonNull ChatColors chatColors) {
    SQLiteDatabase    database = databaseHelper.getWritableDatabase();
    String            where    = CUSTOM_CHAT_COLORS_ID + " = ?";
    String[]          args     = SqlUtil.buildArgs(chatColors.getId().getLongValue());
    List<RecipientId> updated  = new LinkedList<>();

    try (Cursor cursor = database.query(TABLE_NAME, SqlUtil.buildArgs(ID), where, args, null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        updated.add(RecipientId.from(CursorUtil.requireLong(cursor, ID)));
      }
    }

    if (updated.isEmpty()) {
      Log.d(TAG, "No recipients utilizing deleted chat color.");
    } else {
      ContentValues values = new ContentValues(2);

      values.put(CHAT_COLORS, (byte[]) null);
      values.put(CUSTOM_CHAT_COLORS_ID, ChatColors.Id.NotSet.INSTANCE.getLongValue());

      database.update(TABLE_NAME, values, where, args);

      for (RecipientId recipientId : updated) {
        Recipient.live(recipientId).refresh();
      }
    }
  }

  public int getColorUsageCount(@NotNull ChatColors.Id chatColorsId) {
    SQLiteDatabase db         = databaseHelper.getReadableDatabase();
    String[]       projection = SqlUtil.buildArgs("COUNT(*)");
    String         where      = CUSTOM_CHAT_COLORS_ID + " = ?";
    String[]       args       = SqlUtil.buildArgs(chatColorsId.getLongValue());

    try (Cursor cursor = db.query(TABLE_NAME, projection, where, args, null, null, null)) {
      if (cursor == null) {
        return 0;
      } else {
        cursor.moveToFirst();
        return cursor.getInt(0);
      }
    }
  }

  public void clearAllColors() {
    SQLiteDatabase    database  = databaseHelper.getWritableDatabase();
    String            where     = CUSTOM_CHAT_COLORS_ID + " != ?";
    String[]          args      = SqlUtil.buildArgs(ChatColors.Id.NotSet.INSTANCE.getLongValue());
    List<RecipientId> toUpdate  = new LinkedList<>();

    try (Cursor cursor = database.query(TABLE_NAME, SqlUtil.buildArgs(ID), where, args, null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        toUpdate.add(RecipientId.from(CursorUtil.requireLong(cursor, ID)));
      }
    }

    if (toUpdate.isEmpty()) {
      return;
    }

    ContentValues values = new ContentValues();
    values.put(CHAT_COLORS, (byte[]) null);
    values.put(CUSTOM_CHAT_COLORS_ID, ChatColors.Id.NotSet.INSTANCE.getLongValue());

    database.update(TABLE_NAME, values, where, args);

    for (RecipientId id : toUpdate) {
      Recipient.live(id).refresh();
    }
  }

  public void clearColor(@NonNull RecipientId id) {
    ContentValues values = new ContentValues();
    values.put(CHAT_COLORS, (byte[]) null);
    values.put(CUSTOM_CHAT_COLORS_ID, ChatColors.Id.NotSet.INSTANCE.getLongValue());
    if (update(id, values)) {
      Recipient.live(id).refresh();
    }
  }

  public void setColor(@NonNull RecipientId id, @NonNull ChatColors color) {
    ContentValues values = new ContentValues();
    values.put(CHAT_COLORS, color.serialize().toByteArray());
    values.put(CUSTOM_CHAT_COLORS_ID, color.getId().getLongValue());
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
      rotateStorageId(id);
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
      rotateStorageId(id);
      Recipient.live(id).refresh();
    }
    StorageSyncHelper.scheduleSyncForDataChange();
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
    if (update(id, values)) {
      Recipient.live(id).refresh();
    }
  }

  public void setUnidentifiedAccessMode(@NonNull RecipientId id, @NonNull UnidentifiedAccessMode unidentifiedAccessMode) {
    ContentValues values = new ContentValues(1);
    values.put(UNIDENTIFIED_ACCESS_MODE, unidentifiedAccessMode.getMode());
    if (update(id, values)) {
      Recipient.live(id).refresh();
    }
  }

  public void setLastSessionResetTime(@NonNull RecipientId id, DeviceLastResetTime lastResetTime) {
    ContentValues values = new ContentValues(1);
    values.put(LAST_SESSION_RESET, lastResetTime.toByteArray());
    update(id, values);
  }

  public @NonNull DeviceLastResetTime getLastSessionResetTimes(@NonNull RecipientId id) {
    SQLiteDatabase db = databaseHelper.getReadableDatabase();

    try (Cursor cursor = db.query(TABLE_NAME, new String[] {LAST_SESSION_RESET}, ID_WHERE, SqlUtil.buildArgs(id), null, null, null)) {
      if (cursor.moveToFirst()) {
        try {
          byte[] serialized = CursorUtil.requireBlob(cursor, LAST_SESSION_RESET);
          if (serialized != null) {
            return DeviceLastResetTime.parseFrom(serialized);
          } else {
            return DeviceLastResetTime.newBuilder().build();
          }
        } catch (InvalidProtocolBufferException | SQLException e) {
          Log.w(TAG, e);
          return DeviceLastResetTime.newBuilder().build();
        }
      }
    }

    return DeviceLastResetTime.newBuilder().build();
  }

  public void setCapabilities(@NonNull RecipientId id, @NonNull SignalServiceProfile.Capabilities capabilities) {
    long value = 0;

    value = Bitmask.update(value, Capabilities.GROUPS_V2,           Capabilities.BIT_LENGTH, Recipient.Capability.fromBoolean(capabilities.isGv2()).serialize());
    value = Bitmask.update(value, Capabilities.GROUPS_V1_MIGRATION, Capabilities.BIT_LENGTH, Recipient.Capability.fromBoolean(capabilities.isGv1Migration()).serialize());
    value = Bitmask.update(value, Capabilities.SENDER_KEY,          Capabilities.BIT_LENGTH, Recipient.Capability.fromBoolean(capabilities.isSenderKey()).serialize());
    value = Bitmask.update(value, Capabilities.ANNOUNCEMENT_GROUPS, Capabilities.BIT_LENGTH, Recipient.Capability.fromBoolean(capabilities.isAnnouncementGroup()).serialize());

    ContentValues values = new ContentValues(1);
    values.put(CAPABILITIES, value);

    if (update(id, values)) {
      Recipient.live(id).refresh();
    }
  }

  public void setMentionSetting(@NonNull RecipientId id, @NonNull MentionSetting mentionSetting) {
    ContentValues values = new ContentValues();
    values.put(MENTION_SETTING, mentionSetting.getId());
    if (update(id, values)) {
      Recipient.live(id).refresh();
    }
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

    SqlUtil.Query updateQuery = SqlUtil.buildTrueUpdateQuery(selection, args, valuesToCompare);

    if (update(updateQuery, valuesToSet)) {
      rotateStorageId(id);
      Recipient.live(id).refresh();
      StorageSyncHelper.scheduleSyncForDataChange();
      return true;
    }
    return false;
  }

  /**
   * Sets the profile key iff currently null.
   * <p>
   * If it sets it, it also clears out the profile key credential and resets the unidentified access mode.
   * @return true iff changed.
   */
  public boolean setProfileKeyIfAbsent(@NonNull RecipientId id, @NonNull ProfileKey profileKey) {
    SQLiteDatabase database    = databaseHelper.getWritableDatabase();
    String         selection   = ID + " = ? AND " + PROFILE_KEY + " is NULL";
    String[]       args        = new String[]{id.serialize()};
    ContentValues  valuesToSet = new ContentValues(3);

    valuesToSet.put(PROFILE_KEY, Base64.encodeBytes(profileKey.serialize()));
    valuesToSet.putNull(PROFILE_KEY_CREDENTIAL);
    valuesToSet.put(UNIDENTIFIED_ACCESS_MODE, UnidentifiedAccessMode.UNKNOWN.getMode());

    if (database.update(TABLE_NAME, valuesToSet, selection, args) > 0) {
      rotateStorageId(id);
      Recipient.live(id).refresh();
      return true;
    } else {
      return false;
    }
  }

  /**
   * Updates the profile key credential as long as the profile key matches.
   */
  public boolean setProfileKeyCredential(@NonNull RecipientId id,
                                         @NonNull ProfileKey profileKey,
                                         @NonNull ProfileKeyCredential profileKeyCredential)
  {
    String        selection = ID + " = ? AND " + PROFILE_KEY + " = ?";
    String[]      args      = new String[]{id.serialize(), Base64.encodeBytes(profileKey.serialize())};
    ContentValues values    = new ContentValues(1);

    ProfileKeyCredentialColumnData columnData = ProfileKeyCredentialColumnData.newBuilder()
                                                                              .setProfileKey(ByteString.copyFrom(profileKey.serialize()))
                                                                              .setProfileKeyCredential(ByteString.copyFrom(profileKeyCredential.serialize()))
                                                                              .build();

    values.put(PROFILE_KEY_CREDENTIAL, Base64.encodeBytes(columnData.toByteArray()));

    SqlUtil.Query updateQuery = SqlUtil.buildTrueUpdateQuery(selection, args, values);

    boolean updated = update(updateQuery, values);

    if (updated) {
      Recipient.live(id).refresh();
    }

    return updated;
  }

  private void clearProfileKeyCredential(@NonNull RecipientId id) {
    ContentValues values = new ContentValues(1);
    values.putNull(PROFILE_KEY_CREDENTIAL);
    if (update(id, values)) {
      rotateStorageId(id);
      Recipient.live(id).refresh();
    }
  }

  /**
   * Fills in gaps (nulls) in profile key knowledge from new profile keys.
   * <p>
   * If from authoritative source, this will overwrite local, otherwise it will only write to the
   * database if missing.
   */
  public Set<RecipientId> persistProfileKeySet(@NonNull ProfileKeySet profileKeySet) {
    Map<UUID, ProfileKey> profileKeys              = profileKeySet.getProfileKeys();
    Map<UUID, ProfileKey> authoritativeProfileKeys = profileKeySet.getAuthoritativeProfileKeys();
    int                   totalKeys                = profileKeys.size() + authoritativeProfileKeys.size();

    if (totalKeys == 0) {
      return Collections.emptySet();
    }

    Log.i(TAG, String.format(Locale.US, "Persisting %d Profile keys, %d of which are authoritative", totalKeys, authoritativeProfileKeys.size()));

    HashSet<RecipientId> updated = new HashSet<>(totalKeys);
    RecipientId          selfId  = Recipient.self().getId();

    for (Map.Entry<UUID, ProfileKey> entry : profileKeys.entrySet()) {
      RecipientId recipientId = getOrInsertFromUuid(entry.getKey());

      if (setProfileKeyIfAbsent(recipientId, entry.getValue())) {
        Log.i(TAG, "Learned new profile key");
        updated.add(recipientId);
      }
    }

    for (Map.Entry<UUID, ProfileKey> entry : authoritativeProfileKeys.entrySet()) {
      RecipientId recipientId = getOrInsertFromUuid(entry.getKey());

      if (selfId.equals(recipientId)) {
        Log.i(TAG, "Seen authoritative update for self");
        if (!entry.getValue().equals(ProfileKeyUtil.getSelfProfileKey())) {
          Log.w(TAG, "Seen authoritative update for self that didn't match local, scheduling storage sync");
          StorageSyncHelper.scheduleSyncForDataChange();
        }
      } else {
        Log.i(TAG, String.format("Profile key from owner %s", recipientId));
        if (setProfileKey(recipientId, entry.getValue())) {
          Log.i(TAG, "Learned new profile key from owner");
          updated.add(recipientId);
        }
      }
    }

    return updated;
  }

  public @NonNull List<RecipientId> getSimilarRecipientIds(@NonNull Recipient recipient) {
    SQLiteDatabase db   = databaseHelper.getReadableDatabase();
    String[] projection = SqlUtil.buildArgs(ID, "COALESCE(" + nullIfEmpty(SYSTEM_JOINED_NAME) + ", " + nullIfEmpty(PROFILE_JOINED_NAME) + ") AS checked_name");
    String   where      =  "checked_name = ?";

    String[] arguments = SqlUtil.buildArgs(recipient.getProfileName().toString());

    try (Cursor cursor = db.query(TABLE_NAME, projection, where, arguments, null, null, null)) {
      if (cursor == null || cursor.getCount() == 0) {
        return Collections.emptyList();
      }

      List<RecipientId> results = new ArrayList<>(cursor.getCount());
      while (cursor.moveToNext()) {
        results.add(RecipientId.from(CursorUtil.requireLong(cursor, ID)));
      }

      return results;
    }
  }

  public void setProfileName(@NonNull RecipientId id, @NonNull ProfileName profileName) {
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(PROFILE_GIVEN_NAME, profileName.getGivenName());
    contentValues.put(PROFILE_FAMILY_NAME, profileName.getFamilyName());
    contentValues.put(PROFILE_JOINED_NAME, profileName.toString());
    if (update(id, contentValues)) {
      rotateStorageId(id);
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
        rotateStorageId(id);
        StorageSyncHelper.scheduleSyncForDataChange();
      }
    }
  }

  public void setAbout(@NonNull RecipientId id, @Nullable String about, @Nullable String emoji) {
    ContentValues contentValues = new ContentValues();
    contentValues.put(ABOUT, about);
    contentValues.put(ABOUT_EMOJI, emoji);

    if (update(id, contentValues)) {
      Recipient.live(id).refresh();
    }
  }

  public void setProfileSharing(@NonNull RecipientId id, @SuppressWarnings("SameParameterValue") boolean enabled) {
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(PROFILE_SHARING, enabled ? 1 : 0);

    boolean profiledUpdated = update(id, contentValues);

    if (profiledUpdated && enabled) {
      Optional<GroupDatabase.GroupRecord> group = DatabaseFactory.getGroupDatabase(context).getGroup(id);

      if (group.isPresent()) {
        setHasGroupsInCommon(group.get().getMembers());
      }
    }

    if (profiledUpdated) {
      rotateStorageId(id);
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

  public void resetAllWallpaper() {
    SQLiteDatabase                  database      = databaseHelper.getWritableDatabase();
    String[]                        selection     = SqlUtil.buildArgs(ID, WALLPAPER_URI);
    String                          where         = WALLPAPER + " IS NOT NULL";
    List<Pair<RecipientId, String>> idWithWallpaper = new LinkedList<>();

    database.beginTransaction();

    try {
      try (Cursor cursor = database.query(TABLE_NAME, selection, where, null, null, null, null)) {
        while (cursor != null && cursor.moveToNext()) {
          idWithWallpaper.add(new Pair<>(RecipientId.from(CursorUtil.requireInt(cursor, ID)),
                              CursorUtil.getString(cursor, WALLPAPER_URI).orNull()));
        }
      }

      if (idWithWallpaper.isEmpty()) {
        return;
      }

      ContentValues values = new ContentValues(2);
      values.put(WALLPAPER_URI, (String) null);
      values.put(WALLPAPER, (byte[]) null);

      int rowsUpdated = database.update(TABLE_NAME, values, where, null);
      if (rowsUpdated == idWithWallpaper.size()) {
        for (Pair<RecipientId, String> pair : idWithWallpaper) {
          Recipient.live(pair.first()).refresh();
          if (pair.second() != null) {
            WallpaperStorage.onWallpaperDeselected(context, Uri.parse(pair.second()));
          }
        }
      } else {
        throw new AssertionError("expected " + idWithWallpaper.size() + " but got " + rowsUpdated);
      }

    } finally {
      database.setTransactionSuccessful();
      database.endTransaction();
    }

  }

  public void setWallpaper(@NonNull RecipientId id, @Nullable ChatWallpaper chatWallpaper) {
    setWallpaper(id, chatWallpaper != null ? chatWallpaper.serialize() : null);
  }

  private void setWallpaper(@NonNull RecipientId id, @Nullable Wallpaper wallpaper) {
    Uri existingWallpaperUri = getWallpaperUri(id);

    ContentValues values = new ContentValues();
    values.put(WALLPAPER, wallpaper != null ? wallpaper.toByteArray() : null);

    if (wallpaper != null && wallpaper.hasFile()) {
      values.put(WALLPAPER_URI, wallpaper.getFile().getUri());
    } else {
      values.putNull(WALLPAPER_URI);
    }

    if (update(id, values)) {
      Recipient.live(id).refresh();
    }

    if (existingWallpaperUri != null) {
      WallpaperStorage.onWallpaperDeselected(context, existingWallpaperUri);
    }
  }

  public void setDimWallpaperInDarkTheme(@NonNull RecipientId id, boolean enabled) {
    Wallpaper wallpaper = getWallpaper(id);

    if (wallpaper == null) {
      throw new IllegalStateException("No wallpaper set for " + id);
    }

    Wallpaper updated = wallpaper.toBuilder()
                                 .setDimLevelInDarkTheme(enabled ? ChatWallpaper.FIXED_DIM_LEVEL_FOR_DARK_THEME : 0)
                                 .build();

    setWallpaper(id, updated);
  }

  private @Nullable Wallpaper getWallpaper(@NonNull RecipientId id) {
    SQLiteDatabase db = databaseHelper.getReadableDatabase();

    try (Cursor cursor = db.query(TABLE_NAME, new String[] {WALLPAPER}, ID_WHERE, SqlUtil.buildArgs(id), null, null, null)) {
      if (cursor.moveToFirst()) {
        byte[] raw = CursorUtil.requireBlob(cursor, WALLPAPER);

        if (raw != null) {
          try {
            return Wallpaper.parseFrom(raw);
          } catch (InvalidProtocolBufferException e) {
            return null;
          }
        } else {
          return null;
        }
      }
    }

    return null;
  }

  private @Nullable Uri getWallpaperUri(@NonNull RecipientId id) {
    Wallpaper wallpaper = getWallpaper(id);

    if (wallpaper != null && wallpaper.hasFile()) {
      return Uri.parse(wallpaper.getFile().getUri());
    } else {
      return null;
    }
  }

  public int getWallpaperUriUsageCount(@NonNull Uri uri) {
    SQLiteDatabase db    = databaseHelper.getReadableDatabase();
    String         query = WALLPAPER_URI + " = ?";
    String[]       args  = SqlUtil.buildArgs(uri);

    try (Cursor cursor = db.query(TABLE_NAME, new String[] { "COUNT(*)" }, query, args, null, null, null)) {
      if (cursor.moveToFirst()) {
        return cursor.getInt(0);
      }
    }

    return 0;
  }

  /**
   * @return True if setting the phone number resulted in changed recipientId, otherwise false.
   */
  public boolean setPhoneNumber(@NonNull RecipientId id, @NonNull String e164) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.beginTransaction();

    try {
      setPhoneNumberOrThrow(id, e164);
      db.setTransactionSuccessful();
      return false;
    } catch (SQLiteConstraintException e) {
      Log.w(TAG, "[setPhoneNumber] Hit a conflict when trying to update " + id + ". Possibly merging.");

      RecipientSettings existing = getRecipientSettings(id);
      RecipientId       newId    = getAndPossiblyMerge(existing.getUuid(), e164, true);
      Log.w(TAG, "[setPhoneNumber] Resulting id: " + newId);

      db.setTransactionSuccessful();
      return !newId.equals(existing.getId());
    } finally {
      db.endTransaction();
    }
  }

  private void removePhoneNumber(@NonNull RecipientId recipientId, @NonNull SQLiteDatabase db) {
    ContentValues values = new ContentValues();
    values.putNull(PHONE);
    db.update(TABLE_NAME, values, ID_WHERE, SqlUtil.buildArgs(recipientId));
  }

  /**
   * Should only use if you are confident that this will not result in any contact merging.
   */
  public void setPhoneNumberOrThrow(@NonNull RecipientId id, @NonNull String e164) {
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(PHONE, e164);

    if (update(id, contentValues)) {
      rotateStorageId(id);
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

  /**
   * @return True if setting the UUID resulted in changed recipientId, otherwise false.
   */
  public boolean markRegistered(@NonNull RecipientId id, @NonNull UUID uuid) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.beginTransaction();

    try {
      markRegisteredOrThrow(id, uuid);
      db.setTransactionSuccessful();
      return false;
    } catch (SQLiteConstraintException e) {
      Log.w(TAG, "[markRegistered] Hit a conflict when trying to update " + id + ". Possibly merging.");

      RecipientSettings existing = getRecipientSettings(id);
      RecipientId       newId    = getAndPossiblyMerge(uuid, existing.getE164(), true);
      Log.w(TAG, "[markRegistered] Merged into " + newId);

      db.setTransactionSuccessful();
      return !newId.equals(existing.getId());
    } finally {
      db.endTransaction();
    }
  }

  /**
   * Should only use if you are confident that this shouldn't result in any contact merging.
   */
  public void markRegisteredOrThrow(@NonNull RecipientId id, @NonNull UUID uuid) {
    ContentValues contentValues = new ContentValues(2);
    contentValues.put(REGISTERED, RegisteredState.REGISTERED.getId());
    contentValues.put(UUID, uuid.toString().toLowerCase());

    if (update(id, contentValues)) {
      setStorageIdIfNotSet(id);
      Recipient.live(id).refresh();
    }
  }

  /**
   * Marks the user as registered without providing a UUID. This should only be used when one
   * cannot be reasonably obtained. {@link #markRegistered(RecipientId, UUID)} should be strongly
   * preferred.
   */
  public void markRegistered(@NonNull RecipientId id) {
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(REGISTERED, RegisteredState.REGISTERED.getId());

    if (update(id, contentValues)) {
      setStorageIdIfNotSet(id);
      Recipient.live(id).refresh();
    }
  }

  public void markUnregistered(@NonNull RecipientId id) {
    ContentValues contentValues = new ContentValues(2);
    contentValues.put(REGISTERED, RegisteredState.NOT_REGISTERED.getId());
    contentValues.putNull(STORAGE_SERVICE_ID);

    if (update(id, contentValues)) {
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

        if (entry.getValue() != null) {
          values.put(UUID, entry.getValue().toLowerCase());
        }

        try {
          if (update(entry.getKey(), values)) {
            setStorageIdIfNotSet(entry.getKey());
          }
        } catch (SQLiteConstraintException e) {
          Log.w(TAG, "[bulkUpdateRegisteredStatus] Hit a conflict when trying to update " + entry.getKey() + ". Possibly merging.");

          RecipientSettings existing = getRecipientSettings(entry.getKey());
          RecipientId       newId    = getAndPossiblyMerge(UuidUtil.parseOrThrow(entry.getValue()), existing.getE164(), true);
          Log.w(TAG, "[bulkUpdateRegisteredStatus] Merged into " + newId);
        }
      }

      for (RecipientId id : unregistered) {
        ContentValues values = new ContentValues(2);
        values.put(REGISTERED, RegisteredState.NOT_REGISTERED.getId());
        values.putNull(STORAGE_SERVICE_ID);

        update(id, values);
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  /**
   * Handles inserts the (e164, UUID) pairs, which could result in merges. Does not mark users as
   * registered.
   *
   * @return A mapping of (RecipientId, UUID)
   */
  public @NonNull Map<RecipientId, String> bulkProcessCdsResult(@NonNull Map<String, UUID> mapping) {
    SQLiteDatabase               db      = databaseHelper.getWritableDatabase();
    HashMap<RecipientId, String> uuidMap = new HashMap<>();

    db.beginTransaction();
    try {
      for (Map.Entry<String, UUID> entry : mapping.entrySet()) {
        String                e164      = entry.getKey();
        UUID                  uuid      = entry.getValue();
        Optional<RecipientId> uuidEntry = uuid != null ? getByUuid(uuid) : Optional.absent();

        if (uuidEntry.isPresent()) {
          boolean idChanged = setPhoneNumber(uuidEntry.get(), e164);
          if (idChanged) {
            uuidEntry = getByUuid(Objects.requireNonNull(uuid));
          }
        }

        RecipientId id = uuidEntry.isPresent() ? uuidEntry.get() : getOrInsertFromE164(e164);

        uuidMap.put(id, uuid != null ? uuid.toString() : null);
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }

    return uuidMap;
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

    try (Cursor cursor = db.query(TABLE_NAME, ID_PROJECTION, SYSTEM_JOINED_NAME + " IS NOT NULL AND " + SYSTEM_JOINED_NAME + " != \"\"", null, null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        results.add(RecipientId.from(cursor.getLong(cursor.getColumnIndexOrThrow(ID))));
      }
    }

    return results;
  }

  /**
   * We no longer automatically generate a chat color. This method is used only
   * in the case of a legacy migration and otherwise should not be called.
   */
  @Deprecated
  public void updateSystemContactColors() {
    SQLiteDatabase                  db      = databaseHelper.getReadableDatabase();
    Map<RecipientId, ChatColors> updates = new HashMap<>();

    db.beginTransaction();
    try (Cursor cursor = db.query(TABLE_NAME, new String[] {ID, "color", CHAT_COLORS, CUSTOM_CHAT_COLORS_ID, SYSTEM_JOINED_NAME}, SYSTEM_JOINED_NAME + " IS NOT NULL AND " + SYSTEM_JOINED_NAME + " != \"\"", null, null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        long   id                   = CursorUtil.requireLong(cursor, ID);
        String serializedColor      = CursorUtil.requireString(cursor, "color");
        long   customChatColorsId   = CursorUtil.requireLong(cursor, CUSTOM_CHAT_COLORS_ID);
        byte[] serializedChatColors = CursorUtil.requireBlob(cursor, CHAT_COLORS);

        ChatColors chatColors;
        if (serializedChatColors != null) {
          try {
            chatColors = ChatColors.forChatColor(ChatColors.Id.forLongValue(customChatColorsId), ChatColor.parseFrom(serializedChatColors));
          } catch (InvalidProtocolBufferException e) {
            chatColors = null;
          }
        } else {
          chatColors = null;
        }

        if (chatColors != null) {
          return;
        }

        if (serializedColor != null) {
          try {
            chatColors = ChatColorsMapper.getChatColors(MaterialColor.fromSerialized(serializedColor));
          } catch (MaterialColor.UnknownColorException e) {
            return;
          }
        } else {
          return;
        }

        ContentValues contentValues = new ContentValues(1);
        contentValues.put(CHAT_COLORS, chatColors.serialize().toByteArray());
        contentValues.put(CUSTOM_CHAT_COLORS_ID, chatColors.getId().getLongValue());

        db.update(TABLE_NAME, contentValues, ID + " = ?", new String[] { String.valueOf(id) });

        updates.put(RecipientId.from(id), chatColors);
      }
    } finally {
      db.setTransactionSuccessful();
      db.endTransaction();

      Stream.of(updates.entrySet()).forEach(entry -> Recipient.live(entry.getKey()).refresh());
    }
  }

  public @Nullable Cursor getSignalContacts(boolean includeSelf) {
    ContactSearchSelection searchSelection = new ContactSearchSelection.Builder().withRegistered(true)
                                                                                 .withGroups(false)
                                                                                 .excludeId(includeSelf ? null : Recipient.self().getId())
                                                                                 .build();

    String   selection = searchSelection.getWhere();
    String[] args      = searchSelection.getArgs();
    String   orderBy   = SORT_NAME + ", " + SYSTEM_JOINED_NAME + ", " + SEARCH_PROFILE_NAME + ", " + USERNAME + ", " + PHONE;

    return databaseHelper.getReadableDatabase().query(TABLE_NAME, SEARCH_PROJECTION, selection, args, null, null, orderBy);
  }

  public @Nullable Cursor querySignalContacts(@NonNull String query, boolean includeSelf) {
    query = buildCaseInsensitiveGlobPattern(query);

    ContactSearchSelection searchSelection = new ContactSearchSelection.Builder().withRegistered(true)
                                                                                 .withGroups(false)
                                                                                 .excludeId(includeSelf ? null : Recipient.self().getId())
                                                                                 .withSearchQuery(query)
                                                                                 .build();

    String   selection = searchSelection.getWhere();
    String[] args      = searchSelection.getArgs();

    String   orderBy   = SORT_NAME + ", " + SYSTEM_JOINED_NAME + ", " + SEARCH_PROFILE_NAME + ", " + PHONE;

    return databaseHelper.getReadableDatabase().query(TABLE_NAME, SEARCH_PROJECTION, selection, args, null, null, orderBy);
  }

  public @Nullable Cursor getNonSignalContacts() {
    ContactSearchSelection searchSelection = new ContactSearchSelection.Builder().withNonRegistered(true)
                                                                                 .withGroups(false)
                                                                                 .build();

    String   selection = searchSelection.getWhere();
    String[] args      = searchSelection.getArgs();
    String   orderBy   = SYSTEM_JOINED_NAME + ", " + PHONE;

    return databaseHelper.getReadableDatabase().query(TABLE_NAME, SEARCH_PROJECTION, selection, args, null, null, orderBy);
  }

  public @Nullable Cursor queryNonSignalContacts(@NonNull String query) {
    query = buildCaseInsensitiveGlobPattern(query);

    ContactSearchSelection searchSelection = new ContactSearchSelection.Builder().withNonRegistered(true)
                                                                                 .withGroups(false)
                                                                                 .withSearchQuery(query)
                                                                                 .build();

    String   selection = searchSelection.getWhere();
    String[] args      = searchSelection.getArgs();
    String   orderBy   = SYSTEM_JOINED_NAME + ", " + PHONE;

    return databaseHelper.getReadableDatabase().query(TABLE_NAME, SEARCH_PROJECTION, selection, args, null, null, orderBy);
  }

  public @Nullable Cursor getNonGroupContacts(boolean includeSelf) {
    ContactSearchSelection searchSelection = new ContactSearchSelection.Builder().withRegistered(true)
                                                                                 .withNonRegistered(true)
                                                                                 .withGroups(false)
                                                                                 .excludeId(includeSelf ? null : Recipient.self().getId())
                                                                                 .build();

    String orderBy = orderByPreferringAlphaOverNumeric(SORT_NAME) + ", " + PHONE;

    return databaseHelper.getReadableDatabase().query(TABLE_NAME, SEARCH_PROJECTION, searchSelection.where, searchSelection.args, null, null, orderBy);
  }

  public @Nullable Cursor queryNonGroupContacts(@NonNull String query, boolean includeSelf) {
    query = buildCaseInsensitiveGlobPattern(query);

    ContactSearchSelection searchSelection = new ContactSearchSelection.Builder().withRegistered(true)
                                                                                 .withNonRegistered(true)
                                                                                 .withGroups(false)
                                                                                 .excludeId(includeSelf ? null : Recipient.self().getId())
                                                                                 .withSearchQuery(query)
                                                                                 .build();

    String   selection = searchSelection.getWhere();
    String[] args      = searchSelection.getArgs();
    String   orderBy   = orderByPreferringAlphaOverNumeric(SORT_NAME) + ", " + PHONE;

    return databaseHelper.getReadableDatabase().query(TABLE_NAME, SEARCH_PROJECTION, selection, args, null, null, orderBy);
  }

  public @Nullable Cursor queryAllContacts(@NonNull String query) {
    query = buildCaseInsensitiveGlobPattern(query);

    String   selection = BLOCKED + " = ? AND " +
                         "(" +
                           SORT_NAME + " GLOB ? OR " +
                           USERNAME  + " GLOB ? OR " +
                           PHONE     + " GLOB ? OR " +
                           EMAIL     + " GLOB ?" +
                         ")";
    String[] args      = SqlUtil.buildArgs("0", query, query, query, query);

    return databaseHelper.getReadableDatabase().query(TABLE_NAME, SEARCH_PROJECTION, selection, args, null, null, null);
  }

  public @NonNull List<Recipient> queryRecipientsForMentions(@NonNull String query) {
    return queryRecipientsForMentions(query, null);
  }

  public @NonNull List<Recipient> queryRecipientsForMentions(@NonNull String query, @Nullable List<RecipientId> recipientIds) {
    query = buildCaseInsensitiveGlobPattern(query);

    String ids = null;
    if (Util.hasItems(recipientIds)) {
      ids = TextUtils.join(",", Stream.of(recipientIds).map(RecipientId::serialize).toList());
    }

    String   selection = BLOCKED + " = 0 AND " +
                         (ids != null ? ID + " IN (" + ids + ") AND " : "") +
                         SORT_NAME  + " GLOB ?";

    List<Recipient> recipients = new ArrayList<>();
    try (RecipientDatabase.RecipientReader reader = new RecipientReader(databaseHelper.getReadableDatabase().query(TABLE_NAME, MENTION_SEARCH_PROJECTION, selection, SqlUtil.buildArgs(query), null, null, SORT_NAME))) {
      Recipient recipient;
      while ((recipient = reader.getNext()) != null) {
        recipients.add(recipient);
      }
    }
    return recipients;
  }

  /**
   * Builds a case-insensitive GLOB pattern for fuzzy text queries. Works with all unicode
   * characters.
   *
   * Ex:
   *   cat -> [cC][aA][tT]
   */
  private static String buildCaseInsensitiveGlobPattern(@NonNull String query) {
    if (TextUtils.isEmpty(query)) {
      return "*";
    }

    StringBuilder pattern = new StringBuilder();

    for (int i = 0, len = query.codePointCount(0, query.length()); i < len; i++) {
      String point = StringUtil.codePointToString(query.codePointAt(i));

      pattern.append("[");
      pattern.append(point.toLowerCase());
      pattern.append(point.toUpperCase());
      pattern.append(getAccentuatedCharRegex(point.toLowerCase()));
      pattern.append("]");
    }

    return "*" + pattern.toString() + "*";
  }

  private static @NonNull String getAccentuatedCharRegex(@NonNull String query) {
    switch (query) {
      case "a" :
        return "À-Åà-åĀ-ąǍǎǞ-ǡǺ-ǻȀ-ȃȦȧȺɐ-ɒḀḁẚẠ-ặ";
      case "b" :
        return "ßƀ-ƅɃɓḂ-ḇ";
      case "c" :
        return "çÇĆ-čƆ-ƈȻȼɔḈḉ";
      case "d" :
        return "ÐðĎ-đƉ-ƍȡɖɗḊ-ḓ";
      case "e" :
        return "È-Ëè-ëĒ-ěƎ-ƐǝȄ-ȇȨȩɆɇɘ-ɞḔ-ḝẸ-ệ";
      case "f" :
        return "ƑƒḞḟ";
      case "g" :
        return "Ĝ-ģƓǤ-ǧǴǵḠḡ";
      case "h" :
        return "Ĥ-ħƕǶȞȟḢ-ḫẖ";
      case "i" :
        return "Ì-Ïì-ïĨ-ıƖƗǏǐȈ-ȋɨɪḬ-ḯỈ-ị";
      case "j" :
        return "ĴĵǰȷɈɉɟ";
      case "k" :
        return "Ķ-ĸƘƙǨǩḰ-ḵ";
      case "l" :
        return "Ĺ-łƚȴȽɫ-ɭḶ-ḽ";
      case "m" :
        return "Ɯɯ-ɱḾ-ṃ";
      case "n" :
        return "ÑñŃ-ŋƝƞǸǹȠȵɲ-ɴṄ-ṋ";
      case "o" :
        return "Ò-ÖØò-öøŌ-őƟ-ơǑǒǪ-ǭǾǿȌ-ȏȪ-ȱṌ-ṓỌ-ợ";
      case "p" :
        return "ƤƥṔ-ṗ";
      case "q" :
        return "";
      case "r" :
        return "Ŕ-řƦȐ-ȓɌɍṘ-ṟ";
      case "s" :
        return "Ś-šƧƨȘșȿṠ-ṩ";
      case "t" :
        return "Ţ-ŧƫ-ƮȚțȾṪ-ṱẗ";
      case "u" :
        return "Ù-Üù-üŨ-ųƯ-ƱǓ-ǜȔ-ȗɄṲ-ṻỤ-ự";
      case "v" :
        return "ƲɅṼ-ṿ";
      case "w" :
        return "ŴŵẀ-ẉẘ";
      case "x" :
        return "Ẋ-ẍ";
      case "y" :
        return "ÝýÿŶ-ŸƔƳƴȲȳɎɏẎẏỲ-ỹỾỿẙ";
      case "z" :
        return "Ź-žƵƶɀẐ-ẕ";
      default :
        return "";
    }
  }

  public @NonNull List<Recipient> getRecipientsForMultiDeviceSync() {
    String   subquery  = "SELECT " + ThreadDatabase.TABLE_NAME + "." + ThreadDatabase.RECIPIENT_ID + " FROM " + ThreadDatabase.TABLE_NAME;
    String   selection = REGISTERED + " = ? AND " +
                         GROUP_ID   + " IS NULL AND " +
                         ID         + " != ? AND " +
                         "(" + SYSTEM_CONTACT_URI + " NOT NULL OR " + ID + " IN (" + subquery + "))";
    String[] args      = new String[] { String.valueOf(RegisteredState.REGISTERED.getId()), Recipient.self().getId().serialize() };

    List<Recipient> recipients = new ArrayList<>();

    try (Cursor cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME, ID_PROJECTION, selection, args, null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        recipients.add(Recipient.resolved(RecipientId.from(cursor.getLong(cursor.getColumnIndexOrThrow(ID)))));
      }
    }

    return recipients;
  }

  /**
   * @param lastInteractionThreshold Only include contacts that have been interacted with since this time.
   * @param lastProfileFetchThreshold Only include contacts that haven't their profile fetched after this time.
   * @param limit Only return at most this many contact.
   */
  public List<RecipientId> getRecipientsForRoutineProfileFetch(long lastInteractionThreshold, long lastProfileFetchThreshold, int limit) {
    ThreadDatabase threadDatabase                       = DatabaseFactory.getThreadDatabase(context);
    Set<Recipient> recipientsWithinInteractionThreshold = new LinkedHashSet<>();

    try (ThreadDatabase.Reader reader = threadDatabase.readerFor(threadDatabase.getRecentPushConversationList(-1, false))) {
      ThreadRecord record;

      while ((record = reader.getNext()) != null && record.getDate() > lastInteractionThreshold) {
        Recipient recipient = Recipient.resolved(record.getRecipient().getId());

        if (recipient.isGroup()) {
          recipientsWithinInteractionThreshold.addAll(recipient.getParticipants());
        } else {
          recipientsWithinInteractionThreshold.add(recipient);
        }
      }
    }

    return Stream.of(recipientsWithinInteractionThreshold)
                 .filterNot(Recipient::isSelf)
                 .filter(r -> r.getLastProfileFetchTime() < lastProfileFetchThreshold)
                 .limit(limit)
                 .map(Recipient::getId)
                 .toList();
  }

  public void markProfilesFetched(@NonNull Collection<RecipientId> ids, long time) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.beginTransaction();
    try {
      ContentValues values = new ContentValues(1);
      values.put(LAST_PROFILE_FETCH, time);

      for (RecipientId id : ids) {
        db.update(TABLE_NAME, values, ID_WHERE, new String[] { id.serialize() });
      }
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
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

  public void updateStorageId(@NonNull RecipientId recipientId, byte[] id) {
    updateStorageIds(Collections.singletonMap(recipientId, id));
  }

  public void updateStorageIds(@NonNull Map<RecipientId, byte[]> ids) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.beginTransaction();

    try {
      for (Map.Entry<RecipientId, byte[]> entry : ids.entrySet()) {
        ContentValues values = new ContentValues();
        values.put(STORAGE_SERVICE_ID, Base64.encodeBytes(entry.getValue()));
        db.update(TABLE_NAME, values, ID_WHERE, new String[] { entry.getKey().serialize() });
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }

    for (RecipientId id : ids.keySet()) {
      Recipient.live(id).refresh();
    }
  }

  public void markPreMessageRequestRecipientsAsProfileSharingEnabled(long messageRequestEnableTime) {
    String[] whereArgs = SqlUtil.buildArgs(messageRequestEnableTime, messageRequestEnableTime);

    String select = "SELECT r." + ID + " FROM " + TABLE_NAME + " AS r "
                    + "INNER JOIN " + ThreadDatabase.TABLE_NAME + " AS t ON t." + ThreadDatabase.RECIPIENT_ID + " = r." + ID + " WHERE "
                    + "r." + PROFILE_SHARING + " = 0 AND "
                    + "("
                    + "EXISTS(SELECT 1 FROM " + SmsDatabase.TABLE_NAME + " WHERE " + SmsDatabase.THREAD_ID + " = t." + ThreadDatabase.ID + " AND " + SmsDatabase.DATE_RECEIVED + " < ?) "
                    + "OR "
                    + "EXISTS(SELECT 1 FROM " + MmsDatabase.TABLE_NAME + " WHERE " + MmsDatabase.THREAD_ID + " = t." + ThreadDatabase.ID + " AND " + MmsDatabase.DATE_RECEIVED + " < ?) "
                    + ")";

    List<Long> idsToUpdate = new ArrayList<>();
    try (Cursor cursor = databaseHelper.getReadableDatabase().rawQuery(select, whereArgs)) {
      while (cursor.moveToNext()) {
        idsToUpdate.add(CursorUtil.requireLong(cursor, ID));
      }
    }

    if (Util.hasItems(idsToUpdate)) {
      SqlUtil.Query query  = SqlUtil.buildCollectionQuery(ID, idsToUpdate);
      ContentValues values = new ContentValues(1);
      values.put(PROFILE_SHARING, 1);
      databaseHelper.getWritableDatabase().update(TABLE_NAME, values, query.getWhere(), query.getWhereArgs());

      for (long id : idsToUpdate) {
        Recipient.live(RecipientId.from(id)).refresh();
      }
    }
  }

  public void setHasGroupsInCommon(@NonNull List<RecipientId> recipientIds) {
    if (recipientIds.isEmpty()) {
      return;
    }

    SqlUtil.Query  query = SqlUtil.buildCollectionQuery(ID, recipientIds);
    SQLiteDatabase db    = databaseHelper.getWritableDatabase();
    try (Cursor cursor = db.query(TABLE_NAME,
                                  new String[]{ID},
                                  query.getWhere() + " AND " + GROUPS_IN_COMMON + " = 0",
                                  query.getWhereArgs(),
                                  null,
                                  null,
                                  null))
    {
      List<Long> idsToUpdate = new ArrayList<>(cursor.getCount());
      while (cursor.moveToNext()) {
        idsToUpdate.add(CursorUtil.requireLong(cursor, ID));
      }

      if (Util.hasItems(idsToUpdate)) {
        query = SqlUtil.buildCollectionQuery(ID, idsToUpdate);
        ContentValues values = new ContentValues();
        values.put(GROUPS_IN_COMMON, 1);
        int count = db.update(TABLE_NAME, values, query.getWhere(), query.getWhereArgs());
        if (count > 0) {
          for (long id : idsToUpdate) {
            Recipient.live(RecipientId.from(id)).refresh();
          }
        }
      }
    }
  }

  public void manuallyShowAvatar(@NonNull RecipientId recipientId) {
    updateExtras(recipientId, b -> b.setManuallyShownAvatar(true));
  }

  private void updateExtras(@NonNull RecipientId recipientId, @NonNull Function<RecipientExtras.Builder, RecipientExtras.Builder> updater) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.beginTransaction();
    try {
      try (Cursor cursor = db.query(TABLE_NAME, new String[]{ID, EXTRAS}, ID_WHERE, SqlUtil.buildArgs(recipientId), null, null, null)) {
        if (cursor.moveToNext()) {
          RecipientExtras         state        = getRecipientExtras(cursor);
          RecipientExtras.Builder builder      = state != null ? state.toBuilder() : RecipientExtras.newBuilder();
          byte[]                  updatedState = updater.apply(builder).build().toByteArray();
          ContentValues           values       = new ContentValues(1);
          values.put(EXTRAS, updatedState);
          db.update(TABLE_NAME, values, ID_WHERE, SqlUtil.buildArgs(CursorUtil.requireLong(cursor, ID)));
        }
      }
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }

    Recipient.live(recipientId).refresh();
  }

  /**
   * Does not trigger any recipient refreshes -- it is assumed the caller handles this.
   * Will *not* give storageIds to those that shouldn't get them (e.g. MMS groups, unregistered
   * users).
   */
  void rotateStorageId(@NonNull RecipientId recipientId) {
    ContentValues values = new ContentValues(1);
    values.put(STORAGE_SERVICE_ID, Base64.encodeBytes(StorageSyncHelper.generateKey()));

    String   query = ID + " = ? AND (" + GROUP_TYPE + " IN (?, ?) OR " + REGISTERED + " = ?)";
    String[] args  = SqlUtil.buildArgs(recipientId, GroupType.SIGNAL_V1.getId(), GroupType.SIGNAL_V2.getId(), RegisteredState.REGISTERED.getId());

    databaseHelper.getWritableDatabase().update(TABLE_NAME, values, query, args);
  }

  /**
   * Does not trigger any recipient refreshes -- it is assumed the caller handles this.
   */
  void setStorageIdIfNotSet(@NonNull RecipientId recipientId) {
    ContentValues values = new ContentValues(1);
    values.put(STORAGE_SERVICE_ID, Base64.encodeBytes(StorageSyncHelper.generateKey()));

    String   query = ID + " = ? AND " + STORAGE_SERVICE_ID + " IS NULL";
    String[] args  = SqlUtil.buildArgs(recipientId);

    databaseHelper.getWritableDatabase().update(TABLE_NAME, values, query, args);
  }

  /**
   * Updates a group recipient with a new V2 group ID. Should only be done as a part of GV1->GV2
   * migration.
   */
  void updateGroupId(@NonNull GroupId.V1 v1Id, @NonNull GroupId.V2 v2Id) {
    ContentValues values = new ContentValues();
    values.put(GROUP_ID, v2Id.toString());
    values.put(GROUP_TYPE, GroupType.SIGNAL_V2.getId());

    SqlUtil.Query query = SqlUtil.buildTrueUpdateQuery(GROUP_ID + " = ?", SqlUtil.buildArgs(v1Id), values);

    if (update(query, values)) {
      RecipientId id = getByGroupId(v2Id).get();
      rotateStorageId(id);
      Recipient.live(id).refresh();
    }
  }

  /**
   * Will update the database with the content values you specified. It will make an intelligent
   * query such that this will only return true if a row was *actually* updated.
   */
  private boolean update(@NonNull RecipientId id, @NonNull ContentValues contentValues) {
    SqlUtil.Query updateQuery = SqlUtil.buildTrueUpdateQuery(ID_WHERE, SqlUtil.buildArgs(id), contentValues);

    return update(updateQuery, contentValues);
  }

  /**
   * Will update the database with the {@param contentValues} you specified.
   * <p>
   * This will only return true if a row was *actually* updated with respect to the where clause of the {@param updateQuery}.
   */
  private boolean update(@NonNull SqlUtil.Query updateQuery, @NonNull ContentValues contentValues) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();

    return database.update(TABLE_NAME, contentValues, updateQuery.getWhere(), updateQuery.getWhereArgs()) > 0;
  }

  private @NonNull
  Optional<RecipientId> getByColumn(@NonNull String column, String value) {
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
      values.put(AVATAR_COLOR, AvatarColor.random().serialize());

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

  /**
   * Merges one UUID recipient with an E164 recipient. It is assumed that the E164 recipient does
   * *not* have a UUID.
   */
  @SuppressWarnings("ConstantConditions")
  private @NonNull RecipientId merge(@NonNull RecipientId byUuid, @NonNull RecipientId byE164) {
    ensureInTransaction();

    SQLiteDatabase db = databaseHelper.getWritableDatabase();

    RecipientSettings uuidSettings = getRecipientSettings(byUuid);
    RecipientSettings e164Settings = getRecipientSettings(byE164);

    // Recipient
    Log.w(TAG, "Deleting recipient " + byE164, true);
    db.delete(TABLE_NAME, ID_WHERE, SqlUtil.buildArgs(byE164));
    RemappedRecords.getInstance().addRecipient(context, byE164, byUuid);

    ContentValues uuidValues = new ContentValues();
    uuidValues.put(PHONE, e164Settings.getE164());
    uuidValues.put(BLOCKED, e164Settings.isBlocked() || uuidSettings.isBlocked());
    uuidValues.put(MESSAGE_RINGTONE, Optional.fromNullable(uuidSettings.getMessageRingtone()).or(Optional.fromNullable(e164Settings.getMessageRingtone())).transform(Uri::toString).orNull());
    uuidValues.put(MESSAGE_VIBRATE, uuidSettings.getMessageVibrateState() != VibrateState.DEFAULT ? uuidSettings.getMessageVibrateState().getId() : e164Settings.getMessageVibrateState().getId());
    uuidValues.put(CALL_RINGTONE, Optional.fromNullable(uuidSettings.getCallRingtone()).or(Optional.fromNullable(e164Settings.getCallRingtone())).transform(Uri::toString).orNull());
    uuidValues.put(CALL_VIBRATE, uuidSettings.getCallVibrateState() != VibrateState.DEFAULT ? uuidSettings.getCallVibrateState().getId() : e164Settings.getCallVibrateState().getId());
    uuidValues.put(NOTIFICATION_CHANNEL, uuidSettings.getNotificationChannel() != null ? uuidSettings.getNotificationChannel() : e164Settings.getNotificationChannel());
    uuidValues.put(MUTE_UNTIL, uuidSettings.getMuteUntil() > 0 ? uuidSettings.getMuteUntil() : e164Settings.getMuteUntil());
    uuidValues.put(CHAT_COLORS, Optional.fromNullable(uuidSettings.getChatColors()).or(Optional.fromNullable(e164Settings.getChatColors())).transform(colors -> colors.serialize().toByteArray()).orNull());
    uuidValues.put(AVATAR_COLOR, uuidSettings.getAvatarColor().serialize());
    uuidValues.put(CUSTOM_CHAT_COLORS_ID, Optional.fromNullable(uuidSettings.getChatColors()).or(Optional.fromNullable(e164Settings.getChatColors())).transform(colors -> colors.getId().getLongValue()).orNull());
    uuidValues.put(SEEN_INVITE_REMINDER, e164Settings.getInsightsBannerTier().getId());
    uuidValues.put(DEFAULT_SUBSCRIPTION_ID, e164Settings.getDefaultSubscriptionId().or(-1));
    uuidValues.put(MESSAGE_EXPIRATION_TIME, uuidSettings.getExpireMessages() > 0 ? uuidSettings.getExpireMessages() : e164Settings.getExpireMessages());
    uuidValues.put(REGISTERED, RegisteredState.REGISTERED.getId());
    uuidValues.put(SYSTEM_GIVEN_NAME, e164Settings.getSystemProfileName().getGivenName());
    uuidValues.put(SYSTEM_FAMILY_NAME, e164Settings.getSystemProfileName().getFamilyName());
    uuidValues.put(SYSTEM_JOINED_NAME, e164Settings.getSystemProfileName().toString());
    uuidValues.put(SYSTEM_PHOTO_URI, e164Settings.getSystemContactPhotoUri());
    uuidValues.put(SYSTEM_PHONE_LABEL, e164Settings.getSystemPhoneLabel());
    uuidValues.put(SYSTEM_CONTACT_URI, e164Settings.getSystemContactUri());
    uuidValues.put(PROFILE_SHARING, uuidSettings.isProfileSharing() || e164Settings.isProfileSharing());
    uuidValues.put(CAPABILITIES, Math.max(uuidSettings.getCapabilities(), e164Settings.getCapabilities()));
    uuidValues.put(MENTION_SETTING, uuidSettings.getMentionSetting() != MentionSetting.ALWAYS_NOTIFY ? uuidSettings.getMentionSetting().getId() : e164Settings.getMentionSetting().getId());
    if (uuidSettings.getProfileKey() != null) {
      updateProfileValuesForMerge(uuidValues, uuidSettings);
    } else if (e164Settings.getProfileKey() != null) {
      updateProfileValuesForMerge(uuidValues, e164Settings);
    }
    db.update(TABLE_NAME, uuidValues, ID_WHERE, SqlUtil.buildArgs(byUuid));

    // Identities
    db.delete(IdentityDatabase.TABLE_NAME, IdentityDatabase.RECIPIENT_ID + " = ?", SqlUtil.buildArgs(byE164));

    // Group Receipts
    ContentValues groupReceiptValues = new ContentValues();
    groupReceiptValues.put(GroupReceiptDatabase.RECIPIENT_ID, byUuid.serialize());
    db.update(GroupReceiptDatabase.TABLE_NAME, groupReceiptValues, GroupReceiptDatabase.RECIPIENT_ID + " = ?", SqlUtil.buildArgs(byE164));

    // Groups
    GroupDatabase groupDatabase = DatabaseFactory.getGroupDatabase(context);
    for (GroupDatabase.GroupRecord group : groupDatabase.getGroupsContainingMember(byE164, false, true)) {
      List<RecipientId> newMembers = new ArrayList<>(group.getMembers());
      newMembers.remove(byE164);

      ContentValues groupValues = new ContentValues();
      groupValues.put(GroupDatabase.MEMBERS, RecipientId.toSerializedList(newMembers));
      db.update(GroupDatabase.TABLE_NAME, groupValues, GroupDatabase.RECIPIENT_ID + " = ?", SqlUtil.buildArgs(group.getRecipientId()));
    }

    // Threads
    ThreadDatabase.MergeResult threadMerge = DatabaseFactory.getThreadDatabase(context).merge(byUuid, byE164);

    // SMS Messages
    ContentValues smsValues = new ContentValues();
    smsValues.put(SmsDatabase.RECIPIENT_ID, byUuid.serialize());
    db.update(SmsDatabase.TABLE_NAME, smsValues, SmsDatabase.RECIPIENT_ID + " = ?", SqlUtil.buildArgs(byE164));

    if (threadMerge.neededMerge) {
      ContentValues values = new ContentValues();
      values.put(SmsDatabase.THREAD_ID, threadMerge.threadId);
      db.update(SmsDatabase.TABLE_NAME, values, SmsDatabase.THREAD_ID + " = ?", SqlUtil.buildArgs(threadMerge.previousThreadId));
    }

    // MMS Messages
    ContentValues mmsValues = new ContentValues();
    mmsValues.put(MmsDatabase.RECIPIENT_ID, byUuid.serialize());
    db.update(MmsDatabase.TABLE_NAME, mmsValues, MmsDatabase.RECIPIENT_ID + " = ?", SqlUtil.buildArgs(byE164));

    if (threadMerge.neededMerge) {
      ContentValues values = new ContentValues();
      values.put(MmsDatabase.THREAD_ID, threadMerge.threadId);
      db.update(MmsDatabase.TABLE_NAME, values, MmsDatabase.THREAD_ID + " = ?", SqlUtil.buildArgs(threadMerge.previousThreadId));
    }

    // Sessions
    boolean hasE164Session = DatabaseFactory.getSessionDatabase(context).getAllFor(byE164).size() > 0;
    boolean hasUuidSession = DatabaseFactory.getSessionDatabase(context).getAllFor(byUuid).size() > 0;

    if (hasE164Session && hasUuidSession) {
      Log.w(TAG, "Had a session for both users. Deleting the E164.", true);
      db.delete(SessionDatabase.TABLE_NAME, SessionDatabase.RECIPIENT_ID + " = ?", SqlUtil.buildArgs(byE164));
    } else if (hasE164Session && !hasUuidSession) {
      Log.w(TAG, "Had a session for E164, but not UUID. Re-assigning to the UUID.", true);
      ContentValues values = new ContentValues();
      values.put(SessionDatabase.RECIPIENT_ID, byUuid.serialize());
      db.update(SessionDatabase.TABLE_NAME, values, SessionDatabase.RECIPIENT_ID + " = ?", SqlUtil.buildArgs(byE164));
    } else if (!hasE164Session && hasUuidSession) {
      Log.w(TAG, "Had a session for UUID, but not E164. No action necessary.", true);
    } else {
      Log.w(TAG, "Had no sessions. No action necessary.", true);
    }

    // Mentions
    ContentValues mentionRecipientValues = new ContentValues();
    mentionRecipientValues.put(MentionDatabase.RECIPIENT_ID, byUuid.serialize());
    db.update(MentionDatabase.TABLE_NAME, mentionRecipientValues, MentionDatabase.RECIPIENT_ID + " = ?", SqlUtil.buildArgs(byE164));
    if (threadMerge.neededMerge) {
      ContentValues mentionThreadValues = new ContentValues();
      mentionThreadValues.put(MentionDatabase.THREAD_ID, threadMerge.threadId);
      db.update(MentionDatabase.TABLE_NAME, mentionThreadValues, MentionDatabase.THREAD_ID + " = ?", SqlUtil.buildArgs(threadMerge.previousThreadId));
    }

    DatabaseFactory.getThreadDatabase(context).setLastScrolled(threadMerge.threadId, 0);
    DatabaseFactory.getThreadDatabase(context).update(threadMerge.threadId, false, false);

    return byUuid;
  }

  private static void updateProfileValuesForMerge(@NonNull ContentValues values, @NonNull RecipientSettings settings) {
    values.put(PROFILE_KEY, settings.getProfileKey() != null ? Base64.encodeBytes(settings.getProfileKey()) : null);
    values.putNull(PROFILE_KEY_CREDENTIAL);
    values.put(SIGNAL_PROFILE_AVATAR, settings.getProfileAvatar());
    values.put(PROFILE_GIVEN_NAME, settings.getProfileName().getGivenName());
    values.put(PROFILE_FAMILY_NAME, settings.getProfileName().getFamilyName());
    values.put(PROFILE_JOINED_NAME, settings.getProfileName().toString());
  }

  private void ensureInTransaction() {
    if (!databaseHelper.getWritableDatabase().inTransaction()) {
      throw new IllegalStateException("Must be in a transaction!");
    }
  }

  public class BulkOperationsHandle {

    private final SQLiteDatabase database;

    private final Map<RecipientId, PendingContactInfo> pendingContactInfoMap = new HashMap<>();

    BulkOperationsHandle(SQLiteDatabase database) {
      this.database = database;
    }

    public void setSystemContactInfo(@NonNull RecipientId id,
                                     @NonNull ProfileName systemProfileName,
                                     @Nullable String systemDisplayName,
                                     @Nullable String photoUri,
                                     @Nullable String systemPhoneLabel,
                                     int systemPhoneType,
                                     @Nullable String systemContactUri)
    {
      String joinedName = Util.firstNonNull(systemDisplayName, systemProfileName.toString());

      ContentValues refreshQualifyingValues = new ContentValues();
      refreshQualifyingValues.put(SYSTEM_GIVEN_NAME, systemProfileName.getGivenName());
      refreshQualifyingValues.put(SYSTEM_FAMILY_NAME, systemProfileName.getFamilyName());
      refreshQualifyingValues.put(SYSTEM_JOINED_NAME, joinedName);
      refreshQualifyingValues.put(SYSTEM_PHOTO_URI, photoUri);
      refreshQualifyingValues.put(SYSTEM_PHONE_LABEL, systemPhoneLabel);
      refreshQualifyingValues.put(SYSTEM_PHONE_TYPE, systemPhoneType);
      refreshQualifyingValues.put(SYSTEM_CONTACT_URI, systemContactUri);

      boolean updatedValues = update(id, refreshQualifyingValues);

      if (updatedValues) {
        pendingContactInfoMap.put(id, new PendingContactInfo(systemProfileName, photoUri, systemPhoneLabel, systemContactUri));
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
      String   query = SYSTEM_INFO_PENDING + " = ? AND " + STORAGE_SERVICE_ID + " NOT NULL";
      String[] args  = SqlUtil.buildArgs("1");

      try (Cursor cursor = database.query(TABLE_NAME, ID_PROJECTION, query, args, null, null, null)) {
        while (cursor.moveToNext()) {
          RecipientId id = RecipientId.from(CursorUtil.requireString(cursor, ID));
          rotateStorageId(id);
        }
      }
    }

    private void clearSystemDataForPendingInfo() {
      String   query = SYSTEM_INFO_PENDING + " = ?";
      String[] args  = new String[] { "1" };

      ContentValues values = new ContentValues(5);

      values.put(SYSTEM_INFO_PENDING, 0);
      values.put(SYSTEM_GIVEN_NAME, (String) null);
      values.put(SYSTEM_FAMILY_NAME, (String) null);
      values.put(SYSTEM_JOINED_NAME, (String) null);
      values.put(SYSTEM_PHOTO_URI, (String) null);
      values.put(SYSTEM_PHONE_LABEL, (String) null);
      values.put(SYSTEM_CONTACT_URI, (String) null);

      database.update(TABLE_NAME, values, query, args);
    }
  }

  private static @NonNull String nullIfEmpty(String column) {
    return "NULLIF(" + column + ", '')";
  }

  /**
   * By default, SQLite will prefer numbers over letters when sorting. e.g. (b, a, 1) is sorted as (1, a, b).
   * This order by will using a GLOB pattern to instead sort it as (a, b, 1).
   *
   * @param column The name of the column to sort by
   */
  private static @NonNull String orderByPreferringAlphaOverNumeric(@NonNull String column) {
    return "CASE WHEN " + column + " GLOB '[0-9]*' THEN 1 ELSE 0 END, " + column;
  }

  private static @NonNull String removeWhitespace(@NonNull String column) {
    return "REPLACE(" + column + ", ' ', '')";
  }

  public interface ColorUpdater {
    ChatColors update(@NonNull String name, @Nullable MaterialColor materialColor);
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
    private final int                             defaultSubscriptionId;
    private final int                             expireMessages;
    private final RegisteredState                 registered;
    private final byte[]                          profileKey;
    private final ProfileKeyCredential            profileKeyCredential;
    private final ProfileName                     systemProfileName;
    private final String                          systemDisplayName;
    private final String                          systemContactPhoto;
    private final String                          systemPhoneLabel;
    private final String                          systemContactUri;
    private final ProfileName                     signalProfileName;
    private final String                          signalProfileAvatar;
    private final boolean                         hasProfileImage;
    private final boolean                         profileSharing;
    private final long                            lastProfileFetch;
    private final String                          notificationChannel;
    private final UnidentifiedAccessMode          unidentifiedAccessMode;
    private final boolean                         forceSmsSelection;
    private final long                            capabilities;
    private final Recipient.Capability            groupsV2Capability;
    private final Recipient.Capability            groupsV1MigrationCapability;
    private final Recipient.Capability            senderKeyCapability;
    private final Recipient.Capability            announcementGroupCapability;
    private final InsightsBannerTier              insightsBannerTier;
    private final byte[]                          storageId;
    private final MentionSetting                  mentionSetting;
    private final ChatWallpaper                   wallpaper;
    private final ChatColors                      chatColors;
    private final AvatarColor                     avatarColor;
    private final String                          about;
    private final String                          aboutEmoji;
    private final SyncExtras                      syncExtras;
    private final Recipient.Extras                extras;
    private final boolean                         hasGroupsInCommon;

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
                      int defaultSubscriptionId,
                      int expireMessages,
                      @NonNull  RegisteredState registered,
                      @Nullable byte[] profileKey,
                      @Nullable ProfileKeyCredential profileKeyCredential,
                      @NonNull ProfileName systemProfileName,
                      @Nullable String systemDisplayName,
                      @Nullable String systemContactPhoto,
                      @Nullable String systemPhoneLabel,
                      @Nullable String systemContactUri,
                      @NonNull ProfileName signalProfileName,
                      @Nullable String signalProfileAvatar,
                      boolean hasProfileImage,
                      boolean profileSharing,
                      long lastProfileFetch,
                      @Nullable String notificationChannel,
                      @NonNull UnidentifiedAccessMode unidentifiedAccessMode,
                      boolean forceSmsSelection,
                      long capabilities,
                      @NonNull InsightsBannerTier insightsBannerTier,
                      @Nullable byte[] storageId,
                      @NonNull MentionSetting mentionSetting,
                      @Nullable ChatWallpaper wallpaper,
                      @Nullable ChatColors chatColors,
                      @NonNull AvatarColor avatarColor,
                      @Nullable String about,
                      @Nullable String aboutEmoji,
                      @NonNull SyncExtras syncExtras,
                      @Nullable Recipient.Extras extras,
                      boolean hasGroupsInCommon)
    {
      this.id                          = id;
      this.uuid                        = uuid;
      this.username                    = username;
      this.e164                        = e164;
      this.email                       = email;
      this.groupId                     = groupId;
      this.groupType                   = groupType;
      this.blocked                     = blocked;
      this.muteUntil                   = muteUntil;
      this.messageVibrateState         = messageVibrateState;
      this.callVibrateState            = callVibrateState;
      this.messageRingtone             = messageRingtone;
      this.callRingtone                = callRingtone;
      this.defaultSubscriptionId       = defaultSubscriptionId;
      this.expireMessages              = expireMessages;
      this.registered                  = registered;
      this.profileKey                  = profileKey;
      this.profileKeyCredential        = profileKeyCredential;
      this.systemProfileName           = systemProfileName;
      this.systemDisplayName           = systemDisplayName;
      this.systemContactPhoto          = systemContactPhoto;
      this.systemPhoneLabel            = systemPhoneLabel;
      this.systemContactUri            = systemContactUri;
      this.signalProfileName           = signalProfileName;
      this.signalProfileAvatar         = signalProfileAvatar;
      this.hasProfileImage             = hasProfileImage;
      this.profileSharing              = profileSharing;
      this.lastProfileFetch            = lastProfileFetch;
      this.notificationChannel         = notificationChannel;
      this.unidentifiedAccessMode      = unidentifiedAccessMode;
      this.forceSmsSelection           = forceSmsSelection;
      this.capabilities                = capabilities;
      this.groupsV2Capability          = Recipient.Capability.deserialize((int) Bitmask.read(capabilities, Capabilities.GROUPS_V2, Capabilities.BIT_LENGTH));
      this.groupsV1MigrationCapability = Recipient.Capability.deserialize((int) Bitmask.read(capabilities, Capabilities.GROUPS_V1_MIGRATION, Capabilities.BIT_LENGTH));
      this.senderKeyCapability         = Recipient.Capability.deserialize((int) Bitmask.read(capabilities, Capabilities.SENDER_KEY, Capabilities.BIT_LENGTH));
      this.announcementGroupCapability = Recipient.Capability.deserialize((int) Bitmask.read(capabilities, Capabilities.ANNOUNCEMENT_GROUPS, Capabilities.BIT_LENGTH));
      this.insightsBannerTier          = insightsBannerTier;
      this.storageId                   = storageId;
      this.mentionSetting              = mentionSetting;
      this.wallpaper                   = wallpaper;
      this.chatColors                  = chatColors;
      this.avatarColor                 = avatarColor;
      this.about                       = about;
      this.aboutEmoji                  = aboutEmoji;
      this.syncExtras        = syncExtras;
      this.extras            = extras;
      this.hasGroupsInCommon = hasGroupsInCommon;
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

    public @Nullable ProfileKeyCredential getProfileKeyCredential() {
      return profileKeyCredential;
    }

    public @NonNull ProfileName getSystemProfileName() {
      return systemProfileName;
    }

    public @NonNull String getSystemDisplayName() {
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

    public long getLastProfileFetch() {
      return lastProfileFetch;
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

    public @NonNull Recipient.Capability getGroupsV2Capability() {
      return groupsV2Capability;
    }

    public @NonNull Recipient.Capability getGroupsV1MigrationCapability() {
      return groupsV1MigrationCapability;
    }

    public @NonNull Recipient.Capability getSenderKeyCapability() {
      return senderKeyCapability;
    }

    public @NonNull Recipient.Capability getAnnouncementGroupCapability() {
      return announcementGroupCapability;
    }

    public @Nullable byte[] getStorageId() {
      return storageId;
    }

    public @NonNull MentionSetting getMentionSetting() {
      return mentionSetting;
    }

    public @Nullable ChatWallpaper getWallpaper() {
      return wallpaper;
    }

    public @Nullable ChatColors getChatColors() {
      return chatColors;
    }

    public @NonNull AvatarColor getAvatarColor() {
      return avatarColor;
    }

    public @Nullable String getAbout() {
      return about;
    }

    public @Nullable String getAboutEmoji() {
      return aboutEmoji;
    }

    public @NonNull SyncExtras getSyncExtras() {
      return syncExtras;
    }

    public @Nullable Recipient.Extras getExtras() {
      return extras;
    }

    public boolean hasGroupsInCommon() {
      return hasGroupsInCommon;
    }

    long getCapabilities() {
      return capabilities;
    }

    /**
     * A bundle of data that's only necessary when syncing to storage service, not for a
     * {@link Recipient}.
     */
    public static class SyncExtras {
      private final byte[]         storageProto;
      private final GroupMasterKey groupMasterKey;
      private final byte[]         identityKey;
      private final VerifiedStatus identityStatus;
      private final boolean        archived;
      private final boolean        forcedUnread;

      public SyncExtras(@Nullable byte[] storageProto,
                        @Nullable GroupMasterKey groupMasterKey,
                        @Nullable byte[] identityKey,
                        @NonNull VerifiedStatus identityStatus,
                        boolean archived,
                        boolean forcedUnread)
      {
        this.storageProto   = storageProto;
        this.groupMasterKey = groupMasterKey;
        this.identityKey    = identityKey;
        this.identityStatus = identityStatus;
        this.archived       = archived;
        this.forcedUnread   = forcedUnread;
      }

      public @Nullable byte[] getStorageProto() {
        return storageProto;
      }

      public @Nullable GroupMasterKey getGroupMasterKey() {
        return groupMasterKey;
      }

      public boolean isArchived() {
        return archived;
      }

      public @Nullable byte[] getIdentityKey() {
        return identityKey;
      }

      public @NonNull VerifiedStatus getIdentityStatus() {
        return identityStatus;
      }

      public boolean isForcedUnread() {
        return forcedUnread;
      }
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

    public int getCount() {
      if (cursor != null) return cursor.getCount();
      else                return 0;
    }

    public void close() {
      cursor.close();
    }
  }

  public final static class RecipientIdResult {
    private final RecipientId recipientId;
    private final boolean     requiresDirectoryRefresh;

    public RecipientIdResult(@NonNull RecipientId recipientId, boolean requiresDirectoryRefresh) {
      this.recipientId              = recipientId;
      this.requiresDirectoryRefresh = requiresDirectoryRefresh;
    }

    public @NonNull RecipientId getRecipientId() {
      return recipientId;
    }

    public boolean requiresDirectoryRefresh() {
      return requiresDirectoryRefresh;
    }
  }

  private static class PendingContactInfo {

    private final ProfileName profileName;
    private final String      photoUri;
    private final String      phoneLabel;
    private final String      contactUri;

    private PendingContactInfo(@NonNull ProfileName systemProfileName, String photoUri, String phoneLabel, String contactUri) {
      this.profileName = systemProfileName;
      this.photoUri    = photoUri;
      this.phoneLabel  = phoneLabel;
      this.contactUri  = contactUri;
    }
  }

  public static class MissingRecipientException extends IllegalStateException {
    public MissingRecipientException(@Nullable RecipientId id) {
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

  @VisibleForTesting
  static final class ContactSearchSelection {

    static final String FILTER_GROUPS  = " AND " + GROUP_ID + " IS NULL";
    static final String FILTER_ID      = " AND " + ID + " != ?";
    static final String FILTER_BLOCKED = " AND " + BLOCKED + " = ?";

    static final String NON_SIGNAL_CONTACT = REGISTERED + " != ? AND " +
                                             SYSTEM_CONTACT_URI + " NOT NULL AND " +
                                             "(" + PHONE + " NOT NULL OR " + EMAIL + " NOT NULL)";

    static final String QUERY_NON_SIGNAL_CONTACT = NON_SIGNAL_CONTACT +
                                                   " AND (" +
                                                   PHONE + " GLOB ? OR " +
                                                   EMAIL + " GLOB ? OR " +
                                                   SYSTEM_JOINED_NAME + " GLOB ?" +
                                                   ")";

    static final String SIGNAL_CONTACT = REGISTERED + " = ? AND " +
                                         "(" + SYSTEM_JOINED_NAME + " NOT NULL OR " + PROFILE_SHARING + " = ?) AND " +
                                         "(" + SORT_NAME + " NOT NULL OR " + USERNAME + " NOT NULL)";

    static final String QUERY_SIGNAL_CONTACT = SIGNAL_CONTACT + " AND (" +
                                               PHONE + " GLOB ? OR " +
                                               SORT_NAME + " GLOB ? OR " +
                                               USERNAME + " GLOB ?" +
                                               ")";

    private final String   where;
    private final String[] args;

    private ContactSearchSelection(@NonNull String where, @NonNull String[] args) {
      this.where = where;
      this.args  = args;
    }

    String getWhere() {
      return where;
    }

    String[] getArgs() {
      return args;
    }

    @VisibleForTesting
    static final class Builder {

      private boolean     includeRegistered;
      private boolean     includeNonRegistered;
      private RecipientId excludeId;
      private boolean     excludeGroups;
      private String      searchQuery;

      @NonNull Builder withRegistered(boolean includeRegistered) {
        this.includeRegistered = includeRegistered;
        return this;
      }

      @NonNull Builder withNonRegistered(boolean includeNonRegistered) {
        this.includeNonRegistered = includeNonRegistered;
        return this;
      }

      @NonNull Builder excludeId(@Nullable RecipientId recipientId) {
        this.excludeId = recipientId;
        return this;
      }

      @NonNull Builder withGroups(boolean includeGroups) {
        this.excludeGroups = !includeGroups;
        return this;
      }

      @NonNull Builder withSearchQuery(@NonNull String searchQuery) {
        this.searchQuery = searchQuery;
        return this;
      }

      @NonNull ContactSearchSelection build() {
        if (!includeRegistered && !includeNonRegistered) {
          throw new IllegalStateException("Must include either registered or non-registered recipients in search");
        }

        StringBuilder stringBuilder = new StringBuilder("(");
        List<Object>  args          = new LinkedList<>();

        if (includeRegistered) {
          stringBuilder.append("(");

          args.add(RegisteredState.REGISTERED.id);
          args.add(1);

          if (Util.isEmpty(searchQuery)) {
            stringBuilder.append(SIGNAL_CONTACT);
          } else {
            stringBuilder.append(QUERY_SIGNAL_CONTACT);
            args.add(searchQuery);
            args.add(searchQuery);
            args.add(searchQuery);
          }

          stringBuilder.append(")");
        }

        if (includeRegistered && includeNonRegistered) {
          stringBuilder.append(" OR ");
        }

        if (includeNonRegistered) {
          stringBuilder.append("(");
          args.add(RegisteredState.REGISTERED.id);

          if (Util.isEmpty(searchQuery)) {
            stringBuilder.append(NON_SIGNAL_CONTACT);
          } else {
            stringBuilder.append(QUERY_NON_SIGNAL_CONTACT);
            args.add(searchQuery);
            args.add(searchQuery);
            args.add(searchQuery);
          }

          stringBuilder.append(")");
        }

        stringBuilder.append(")");
        stringBuilder.append(FILTER_BLOCKED);
        args.add(0);

        if (excludeGroups) {
          stringBuilder.append(FILTER_GROUPS);
        }

        if (excludeId != null) {
          stringBuilder.append(FILTER_ID);
          args.add(excludeId.serialize());
        }

        return new ContactSearchSelection(stringBuilder.toString(), args.stream().map(Object::toString).toArray(String[]::new));
      }
    }
  }
}
