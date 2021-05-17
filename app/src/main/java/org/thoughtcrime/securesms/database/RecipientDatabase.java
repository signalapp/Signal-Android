package org.thoughtcrime.securesms.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import net.sqlcipher.database.SQLiteDatabase;
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;

import org.session.libsession.utilities.color.MaterialColor;
import org.session.libsession.messaging.threads.Address;
import org.session.libsession.messaging.threads.recipients.Recipient;
import org.session.libsession.messaging.threads.recipients.Recipient.*;
import org.session.libsignal.utilities.Base64;
import org.session.libsession.utilities.Util;

import org.session.libsignal.utilities.guava.Optional;
import org.session.libsignal.utilities.Log;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

public class RecipientDatabase extends Database {

  private static final String TAG = RecipientDatabase.class.getSimpleName();

          static final String TABLE_NAME               = "recipient_preferences";
  private static final String ID                       = "_id";
  public  static final String ADDRESS                  = "recipient_ids";
  private static final String BLOCK                    = "block";
  private static final String NOTIFICATION             = "notification";
  private static final String VIBRATE                  = "vibrate";
  private static final String MUTE_UNTIL               = "mute_until";
  private static final String COLOR                    = "color";
  private static final String SEEN_INVITE_REMINDER     = "seen_invite_reminder";
  private static final String DEFAULT_SUBSCRIPTION_ID  = "default_subscription_id";
  private static final String EXPIRE_MESSAGES          = "expire_messages";
  private static final String REGISTERED               = "registered";
  private static final String PROFILE_KEY              = "profile_key";
  private static final String SYSTEM_DISPLAY_NAME      = "system_display_name";
  private static final String SYSTEM_PHOTO_URI         = "system_contact_photo";
  private static final String SYSTEM_PHONE_LABEL       = "system_phone_label";
  private static final String SYSTEM_CONTACT_URI       = "system_contact_uri";
  private static final String SIGNAL_PROFILE_NAME      = "signal_profile_name";
  private static final String SIGNAL_PROFILE_AVATAR    = "signal_profile_avatar";
  private static final String PROFILE_SHARING          = "profile_sharing_approval";
  private static final String CALL_RINGTONE            = "call_ringtone";
  private static final String CALL_VIBRATE             = "call_vibrate";
  private static final String NOTIFICATION_CHANNEL     = "notification_channel";
  private static final String UNIDENTIFIED_ACCESS_MODE = "unidentified_access_mode";
  private static final String FORCE_SMS_SELECTION      = "force_sms_selection";

  private static final String[] RECIPIENT_PROJECTION = new String[] {
      BLOCK, NOTIFICATION, CALL_RINGTONE, VIBRATE, CALL_VIBRATE, MUTE_UNTIL, COLOR, SEEN_INVITE_REMINDER, DEFAULT_SUBSCRIPTION_ID, EXPIRE_MESSAGES, REGISTERED,
      PROFILE_KEY, SYSTEM_DISPLAY_NAME, SYSTEM_PHOTO_URI, SYSTEM_PHONE_LABEL, SYSTEM_CONTACT_URI,
      SIGNAL_PROFILE_NAME, SIGNAL_PROFILE_AVATAR, PROFILE_SHARING, NOTIFICATION_CHANNEL,
      UNIDENTIFIED_ACCESS_MODE,
      FORCE_SMS_SELECTION,
  };

  static final List<String> TYPED_RECIPIENT_PROJECTION = Stream.of(RECIPIENT_PROJECTION)
                                                               .map(columnName -> TABLE_NAME + "." + columnName)
                                                               .toList();

  public static final String CREATE_TABLE =
      "CREATE TABLE " + TABLE_NAME +
          " (" + ID + " INTEGER PRIMARY KEY, " +
          ADDRESS + " TEXT UNIQUE, " +
          BLOCK + " INTEGER DEFAULT 0," +
          NOTIFICATION + " TEXT DEFAULT NULL, " +
          VIBRATE + " INTEGER DEFAULT " + Recipient.VibrateState.DEFAULT.getId() + ", " +
          MUTE_UNTIL + " INTEGER DEFAULT 0, " +
          COLOR + " TEXT DEFAULT NULL, " +
          SEEN_INVITE_REMINDER + " INTEGER DEFAULT 0, " +
          DEFAULT_SUBSCRIPTION_ID + " INTEGER DEFAULT -1, " +
          EXPIRE_MESSAGES + " INTEGER DEFAULT 0, " +
          REGISTERED + " INTEGER DEFAULT 0, " +
          SYSTEM_DISPLAY_NAME + " TEXT DEFAULT NULL, " +
          SYSTEM_PHOTO_URI + " TEXT DEFAULT NULL, " +
          SYSTEM_PHONE_LABEL + " TEXT DEFAULT NULL, " +
          SYSTEM_CONTACT_URI + " TEXT DEFAULT NULL, " +
          PROFILE_KEY + " TEXT DEFAULT NULL, " +
          SIGNAL_PROFILE_NAME + " TEXT DEFAULT NULL, " +
          SIGNAL_PROFILE_AVATAR + " TEXT DEFAULT NULL, " +
          PROFILE_SHARING + " INTEGER DEFAULT 0, " +
          CALL_RINGTONE + " TEXT DEFAULT NULL, " +
          CALL_VIBRATE + " INTEGER DEFAULT " + Recipient.VibrateState.DEFAULT.getId() + ", " +
          NOTIFICATION_CHANNEL + " TEXT DEFAULT NULL, " +
          UNIDENTIFIED_ACCESS_MODE + " INTEGER DEFAULT 0, " +
          FORCE_SMS_SELECTION + " INTEGER DEFAULT 0);";

  public RecipientDatabase(Context context, SQLCipherOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  public RecipientReader getRecipientsWithNotificationChannels() {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    Cursor         cursor   = database.query(TABLE_NAME, new String[] {ID, ADDRESS}, NOTIFICATION_CHANNEL  + " NOT NULL",
                                             null, null, null, null, null);

    return new RecipientReader(context, cursor);
  }

  public Optional<RecipientSettings> getRecipientSettings(@NonNull Address address) {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    Cursor         cursor   = null;

    try {
      cursor = database.query(TABLE_NAME, null, ADDRESS + " = ?", new String[] {address.serialize()}, null, null, null);

      if (cursor != null && cursor.moveToNext()) {
        return getRecipientSettings(cursor);
      }

      return Optional.absent();
    } finally {
      if (cursor != null) cursor.close();
    }
  }

  Optional<RecipientSettings> getRecipientSettings(@NonNull Cursor cursor) {
    boolean blocked                = cursor.getInt(cursor.getColumnIndexOrThrow(BLOCK))                == 1;
    String  messageRingtone        = cursor.getString(cursor.getColumnIndexOrThrow(NOTIFICATION));
    String  callRingtone           = cursor.getString(cursor.getColumnIndexOrThrow(CALL_RINGTONE));
    int     messageVibrateState    = cursor.getInt(cursor.getColumnIndexOrThrow(VIBRATE));
    int     callVibrateState       = cursor.getInt(cursor.getColumnIndexOrThrow(CALL_VIBRATE));
    long    muteUntil              = cursor.getLong(cursor.getColumnIndexOrThrow(MUTE_UNTIL));
    String  serializedColor        = cursor.getString(cursor.getColumnIndexOrThrow(COLOR));
    int     defaultSubscriptionId  = cursor.getInt(cursor.getColumnIndexOrThrow(DEFAULT_SUBSCRIPTION_ID));
    int     expireMessages         = cursor.getInt(cursor.getColumnIndexOrThrow(EXPIRE_MESSAGES));
    int     registeredState        = cursor.getInt(cursor.getColumnIndexOrThrow(REGISTERED));
    String  profileKeyString       = cursor.getString(cursor.getColumnIndexOrThrow(PROFILE_KEY));
    String  systemDisplayName      = cursor.getString(cursor.getColumnIndexOrThrow(SYSTEM_DISPLAY_NAME));
    String  systemContactPhoto     = cursor.getString(cursor.getColumnIndexOrThrow(SYSTEM_PHOTO_URI));
    String  systemPhoneLabel       = cursor.getString(cursor.getColumnIndexOrThrow(SYSTEM_PHONE_LABEL));
    String  systemContactUri       = cursor.getString(cursor.getColumnIndexOrThrow(SYSTEM_CONTACT_URI));
    String  signalProfileName      = cursor.getString(cursor.getColumnIndexOrThrow(SIGNAL_PROFILE_NAME));
    String  signalProfileAvatar    = cursor.getString(cursor.getColumnIndexOrThrow(SIGNAL_PROFILE_AVATAR));
    boolean profileSharing         = cursor.getInt(cursor.getColumnIndexOrThrow(PROFILE_SHARING))      == 1;
    String  notificationChannel    = cursor.getString(cursor.getColumnIndexOrThrow(NOTIFICATION_CHANNEL));
    int     unidentifiedAccessMode = cursor.getInt(cursor.getColumnIndexOrThrow(UNIDENTIFIED_ACCESS_MODE));
    boolean forceSmsSelection      = cursor.getInt(cursor.getColumnIndexOrThrow(FORCE_SMS_SELECTION))  == 1;

    MaterialColor color;
    byte[] profileKey = null;

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
    }

    return Optional.of(new RecipientSettings(blocked, muteUntil,
                                             Recipient.VibrateState.fromId(messageVibrateState),
                                             Recipient.VibrateState.fromId(callVibrateState),
                                             Util.uri(messageRingtone), Util.uri(callRingtone),
                                             color, defaultSubscriptionId, expireMessages,
                                             Recipient.RegisteredState.fromId(registeredState),
                                             profileKey, systemDisplayName, systemContactPhoto,
                                             systemPhoneLabel, systemContactUri,
                                             signalProfileName, signalProfileAvatar, profileSharing,
                                             notificationChannel, Recipient.UnidentifiedAccessMode.fromMode(unidentifiedAccessMode),
                                             forceSmsSelection));
  }

  public void setColor(@NonNull Recipient recipient, @NonNull MaterialColor color) {
    ContentValues values = new ContentValues();
    values.put(COLOR, color.serialize());
    updateOrInsert(recipient.getAddress(), values);
    recipient.resolve().setColor(color);
  }

  public void setDefaultSubscriptionId(@NonNull Recipient recipient, int defaultSubscriptionId) {
    ContentValues values = new ContentValues();
    values.put(DEFAULT_SUBSCRIPTION_ID, defaultSubscriptionId);
    updateOrInsert(recipient.getAddress(), values);
    recipient.resolve().setDefaultSubscriptionId(Optional.of(defaultSubscriptionId));
  }

  public void setForceSmsSelection(@NonNull Recipient recipient, boolean forceSmsSelection) {
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(FORCE_SMS_SELECTION, forceSmsSelection ? 1 : 0);
    updateOrInsert(recipient.getAddress(), contentValues);
    recipient.resolve().setForceSmsSelection(forceSmsSelection);
  }

  public void setBlocked(@NonNull Recipient recipient, boolean blocked) {
    ContentValues values = new ContentValues();
    values.put(BLOCK, blocked ? 1 : 0);
    updateOrInsert(recipient.getAddress(), values);
    recipient.resolve().setBlocked(blocked);
  }

  public void setMuted(@NonNull Recipient recipient, long until) {
    ContentValues values = new ContentValues();
    values.put(MUTE_UNTIL, until);
    updateOrInsert(recipient.getAddress(), values);
    recipient.resolve().setMuted(until);
  }

  public void setExpireMessages(@NonNull Recipient recipient, int expiration) {
    recipient.setExpireMessages(expiration);

    ContentValues values = new ContentValues(1);
    values.put(EXPIRE_MESSAGES, expiration);
    updateOrInsert(recipient.getAddress(), values);
    recipient.resolve().setExpireMessages(expiration);
  }

  public void setUnidentifiedAccessMode(@NonNull Recipient recipient, @NonNull UnidentifiedAccessMode unidentifiedAccessMode) {
    ContentValues values = new ContentValues(1);
    values.put(UNIDENTIFIED_ACCESS_MODE, unidentifiedAccessMode.getMode());
    updateOrInsert(recipient.getAddress(), values);
    recipient.resolve().setUnidentifiedAccessMode(unidentifiedAccessMode);
  }

  public void setProfileKey(@NonNull Recipient recipient, @Nullable byte[] profileKey) {
    ContentValues values = new ContentValues(1);
    values.put(PROFILE_KEY, profileKey == null ? null : Base64.encodeBytes(profileKey));
    updateOrInsert(recipient.getAddress(), values);
    recipient.resolve().setProfileKey(profileKey);
  }

  public void setProfileAvatar(@NonNull Recipient recipient, @Nullable String profileAvatar) {
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(SIGNAL_PROFILE_AVATAR, profileAvatar);
    updateOrInsert(recipient.getAddress(), contentValues);
    recipient.resolve().setProfileAvatar(profileAvatar);
  }

  public void setProfileName(@NonNull Recipient recipient, @Nullable String profileName) {
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(SYSTEM_DISPLAY_NAME, profileName);
    updateOrInsert(recipient.getAddress(), contentValues);
    recipient.resolve().setProfileName(profileName);
  }

  public void setProfileSharing(@NonNull Recipient recipient, boolean enabled) {
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(PROFILE_SHARING, enabled ? 1 : 0);
    updateOrInsert(recipient.getAddress(), contentValues);
    recipient.setProfileSharing(enabled);
  }

  public void setNotificationChannel(@NonNull Recipient recipient, @Nullable String notificationChannel) {
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(NOTIFICATION_CHANNEL, notificationChannel);
    updateOrInsert(recipient.getAddress(), contentValues);
    recipient.setNotificationChannel(notificationChannel);
  }

  public void setRegistered(@NonNull Recipient recipient, RegisteredState registeredState) {
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(REGISTERED, registeredState.getId());
    updateOrInsert(recipient.getAddress(), contentValues);
    recipient.setRegistered(registeredState);
  }

  private void updateOrInsert(Address address, ContentValues contentValues) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();

    database.beginTransaction();

    int updated = database.update(TABLE_NAME, contentValues, ADDRESS + " = ?",
                                  new String[] {address.serialize()});

    if (updated < 1) {
      contentValues.put(ADDRESS, address.serialize());
      database.insert(TABLE_NAME, null, contentValues);
    }

    database.setTransactionSuccessful();
    database.endTransaction();
  }

  public static class RecipientReader implements Closeable {

    private final Context context;
    private final Cursor  cursor;

    RecipientReader(Context context, Cursor cursor) {
      this.context = context;
      this.cursor  = cursor;
    }

    public @NonNull Recipient getCurrent() {
      String serialized = cursor.getString(cursor.getColumnIndexOrThrow(ADDRESS));
      return Recipient.from(context, Address.fromSerialized(serialized), false);
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
}
