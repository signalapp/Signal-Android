package org.thoughtcrime.securesms.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import org.thoughtcrime.securesms.logging.Log;

import com.annimon.stream.Stream;

import net.sqlcipher.database.SQLiteDatabase;

import org.thoughtcrime.securesms.color.MaterialColor;
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RecipientDatabase extends Database {

  private static final String TAG = RecipientDatabase.class.getSimpleName();

          static final String TABLE_NAME              = "recipient_preferences";
  private static final String ID                      = "_id";
          static final String ADDRESS                 = "recipient_ids";
  private static final String BLOCK                   = "block";
  private static final String NOTIFICATION            = "notification";
  private static final String VIBRATE                 = "vibrate";
  private static final String MUTE_UNTIL              = "mute_until";
  private static final String COLOR                   = "color";
  private static final String SEEN_INVITE_REMINDER    = "seen_invite_reminder";
  private static final String DEFAULT_SUBSCRIPTION_ID = "default_subscription_id";
  private static final String EXPIRE_MESSAGES         = "expire_messages";
  private static final String REGISTERED              = "registered";
  private static final String PROFILE_KEY             = "profile_key";
  private static final String SYSTEM_DISPLAY_NAME     = "system_display_name";
  private static final String SYSTEM_PHOTO_URI        = "system_contact_photo";
  private static final String SYSTEM_PHONE_LABEL      = "system_phone_label";
  private static final String SYSTEM_CONTACT_URI      = "system_contact_uri";
  private static final String SIGNAL_PROFILE_NAME     = "signal_profile_name";
  private static final String SIGNAL_PROFILE_AVATAR   = "signal_profile_avatar";
  private static final String PROFILE_SHARING         = "profile_sharing_approval";
  private static final String CALL_RINGTONE           = "call_ringtone";
  private static final String CALL_VIBRATE            = "call_vibrate";
  private static final String NOTIFICATION_CHANNEL    = "notification_channel";

  private static final String[] RECIPIENT_PROJECTION = new String[] {
      BLOCK, NOTIFICATION, CALL_RINGTONE, VIBRATE, CALL_VIBRATE, MUTE_UNTIL, COLOR, SEEN_INVITE_REMINDER, DEFAULT_SUBSCRIPTION_ID, EXPIRE_MESSAGES, REGISTERED,
      PROFILE_KEY, SYSTEM_DISPLAY_NAME, SYSTEM_PHOTO_URI, SYSTEM_PHONE_LABEL, SYSTEM_CONTACT_URI,
      SIGNAL_PROFILE_NAME, SIGNAL_PROFILE_AVATAR, PROFILE_SHARING, NOTIFICATION_CHANNEL
  };

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

  public static final String CREATE_TABLE =
      "CREATE TABLE " + TABLE_NAME +
          " (" + ID + " INTEGER PRIMARY KEY, " +
          ADDRESS + " TEXT UNIQUE, " +
          BLOCK + " INTEGER DEFAULT 0," +
          NOTIFICATION + " TEXT DEFAULT NULL, " +
          VIBRATE + " INTEGER DEFAULT " + VibrateState.DEFAULT.getId() + ", " +
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
          CALL_VIBRATE + " INTEGER DEFAULT " + VibrateState.DEFAULT.getId() + ", " +
          NOTIFICATION_CHANNEL + " TEXT DEFAULT NULL);";

  public RecipientDatabase(Context context, SQLCipherOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  public Cursor getBlocked() {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();

    return database.query(TABLE_NAME, new String[] {ID, ADDRESS}, BLOCK + " = 1",
                          null, null, null, null, null);
  }

  public RecipientReader readerForBlocked(Cursor cursor) {
    return new RecipientReader(context, cursor);
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
    boolean blocked               = cursor.getInt(cursor.getColumnIndexOrThrow(BLOCK))                == 1;
    String  messageRingtone       = cursor.getString(cursor.getColumnIndexOrThrow(NOTIFICATION));
    String  callRingtone          = cursor.getString(cursor.getColumnIndexOrThrow(CALL_RINGTONE));
    int     messageVibrateState   = cursor.getInt(cursor.getColumnIndexOrThrow(VIBRATE));
    int     callVibrateState      = cursor.getInt(cursor.getColumnIndexOrThrow(CALL_VIBRATE));
    long    muteUntil             = cursor.getLong(cursor.getColumnIndexOrThrow(MUTE_UNTIL));
    String  serializedColor       = cursor.getString(cursor.getColumnIndexOrThrow(COLOR));
    boolean seenInviteReminder    = cursor.getInt(cursor.getColumnIndexOrThrow(SEEN_INVITE_REMINDER)) == 1;
    int     defaultSubscriptionId = cursor.getInt(cursor.getColumnIndexOrThrow(DEFAULT_SUBSCRIPTION_ID));
    int     expireMessages        = cursor.getInt(cursor.getColumnIndexOrThrow(EXPIRE_MESSAGES));
    int     registeredState       = cursor.getInt(cursor.getColumnIndexOrThrow(REGISTERED));
    String  profileKeyString      = cursor.getString(cursor.getColumnIndexOrThrow(PROFILE_KEY));
    String  systemDisplayName     = cursor.getString(cursor.getColumnIndexOrThrow(SYSTEM_DISPLAY_NAME));
    String  systemContactPhoto    = cursor.getString(cursor.getColumnIndexOrThrow(SYSTEM_PHOTO_URI));
    String  systemPhoneLabel      = cursor.getString(cursor.getColumnIndexOrThrow(SYSTEM_PHONE_LABEL));
    String  systemContactUri      = cursor.getString(cursor.getColumnIndexOrThrow(SYSTEM_CONTACT_URI));
    String  signalProfileName     = cursor.getString(cursor.getColumnIndexOrThrow(SIGNAL_PROFILE_NAME));
    String  signalProfileAvatar   = cursor.getString(cursor.getColumnIndexOrThrow(SIGNAL_PROFILE_AVATAR));
    boolean profileSharing        = cursor.getInt(cursor.getColumnIndexOrThrow(PROFILE_SHARING))      == 1;
    String  notificationChannel   = cursor.getString(cursor.getColumnIndexOrThrow(NOTIFICATION_CHANNEL));

    MaterialColor color;
    byte[]        profileKey = null;

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
                                             VibrateState.fromId(messageVibrateState),
                                             VibrateState.fromId(callVibrateState),
                                             Util.uri(messageRingtone), Util.uri(callRingtone),
                                             color, seenInviteReminder,
                                             defaultSubscriptionId, expireMessages,
                                             RegisteredState.fromId(registeredState),
                                             profileKey, systemDisplayName, systemContactPhoto,
                                             systemPhoneLabel, systemContactUri,
                                             signalProfileName, signalProfileAvatar, profileSharing,
                                             notificationChannel));
  }

  public BulkOperationsHandle resetAllSystemContactInfo() {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    database.beginTransaction();

    ContentValues contentValues = new ContentValues(1);
    contentValues.put(SYSTEM_DISPLAY_NAME, (String)null);
    contentValues.put(SYSTEM_PHOTO_URI, (String)null);
    contentValues.put(SYSTEM_PHONE_LABEL, (String)null);
    contentValues.put(SYSTEM_CONTACT_URI, (String)null);

    database.update(TABLE_NAME, contentValues, null, null);

    return new BulkOperationsHandle(database);
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

  public void setBlocked(@NonNull Recipient recipient, boolean blocked) {
    ContentValues values = new ContentValues();
    values.put(BLOCK, blocked ? 1 : 0);
    updateOrInsert(recipient.getAddress(), values);
    recipient.resolve().setBlocked(blocked);
  }

  public void setMessageRingtone(@NonNull Recipient recipient, @Nullable Uri notification) {
    ContentValues values = new ContentValues();
    values.put(NOTIFICATION, notification == null ? null : notification.toString());
    updateOrInsert(recipient.getAddress(), values);
    recipient.resolve().setMessageRingtone(notification);
  }

  public void setCallRingtone(@NonNull Recipient recipient, @Nullable Uri ringtone) {
    ContentValues values = new ContentValues();
    values.put(CALL_RINGTONE, ringtone == null ? null : ringtone.toString());
    updateOrInsert(recipient.getAddress(), values);
    recipient.resolve().setCallRingtone(ringtone);
  }

  public void setMessageVibrate(@NonNull Recipient recipient, @NonNull VibrateState enabled) {
    ContentValues values = new ContentValues();
    values.put(VIBRATE, enabled.getId());
    updateOrInsert(recipient.getAddress(), values);
    recipient.resolve().setMessageVibrate(enabled);
  }

  public void setCallVibrate(@NonNull Recipient recipient, @NonNull VibrateState enabled) {
    ContentValues values = new ContentValues();
    values.put(CALL_VIBRATE, enabled.getId());
    updateOrInsert(recipient.getAddress(), values);
    recipient.resolve().setCallVibrate(enabled);
  }

  public void setMuted(@NonNull Recipient recipient, long until) {
    ContentValues values = new ContentValues();
    values.put(MUTE_UNTIL, until);
    updateOrInsert(recipient.getAddress(), values);
    recipient.resolve().setMuted(until);
  }

  public void setSeenInviteReminder(@NonNull Recipient recipient, @SuppressWarnings("SameParameterValue") boolean seen) {
    ContentValues values = new ContentValues(1);
    values.put(SEEN_INVITE_REMINDER, seen ? 1 : 0);
    updateOrInsert(recipient.getAddress(), values);
    recipient.resolve().setHasSeenInviteReminder(seen);
  }

  public void setExpireMessages(@NonNull Recipient recipient, int expiration) {
    recipient.setExpireMessages(expiration);

    ContentValues values = new ContentValues(1);
    values.put(EXPIRE_MESSAGES, expiration);
    updateOrInsert(recipient.getAddress(), values);
    recipient.resolve().setExpireMessages(expiration);
  }

  public void setProfileKey(@NonNull Recipient recipient, @Nullable byte[] profileKey) {
    ContentValues values = new ContentValues(1);
    values.put(PROFILE_KEY, profileKey == null ? null : Base64.encodeBytes(profileKey));
    updateOrInsert(recipient.getAddress(), values);
    recipient.resolve().setProfileKey(profileKey);
  }

  public void setProfileName(@NonNull Recipient recipient, @Nullable String profileName) {
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(SIGNAL_PROFILE_NAME, profileName);
    updateOrInsert(recipient.getAddress(), contentValues);
    recipient.resolve().setProfileName(profileName);
  }

  public void setProfileAvatar(@NonNull Recipient recipient, @Nullable String profileAvatar) {
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(SIGNAL_PROFILE_AVATAR, profileAvatar);
    updateOrInsert(recipient.getAddress(), contentValues);
    recipient.resolve().setProfileAvatar(profileAvatar);
  }

  public void setProfileSharing(@NonNull Recipient recipient, @SuppressWarnings("SameParameterValue") boolean enabled) {
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

  public Set<Address> getAllAddresses() {
    SQLiteDatabase db      = databaseHelper.getReadableDatabase();
    Set<Address>   results = new HashSet<>();

    try (Cursor cursor = db.query(TABLE_NAME, new String[] {ADDRESS}, null, null, null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        results.add(Address.fromExternal(context, cursor.getString(0)));
      }
    }

    return results;
  }

  public void setRegistered(@NonNull Recipient recipient, RegisteredState registeredState) {
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(REGISTERED, registeredState.getId());
    updateOrInsert(recipient.getAddress(), contentValues);
    recipient.setRegistered(registeredState);
  }

  public void setRegistered(@NonNull List<Address> activeAddresses,
                            @NonNull List<Address> inactiveAddresses)
  {
    for (Address activeAddress : activeAddresses) {
      ContentValues contentValues = new ContentValues(1);
      contentValues.put(REGISTERED, RegisteredState.REGISTERED.getId());

      updateOrInsert(activeAddress, contentValues);
      Recipient.applyCached(activeAddress, recipient -> recipient.setRegistered(RegisteredState.REGISTERED));
    }

    for (Address inactiveAddress : inactiveAddresses) {
      ContentValues contentValues = new ContentValues(1);
      contentValues.put(REGISTERED, RegisteredState.NOT_REGISTERED.getId());

      updateOrInsert(inactiveAddress, contentValues);
      Recipient.applyCached(inactiveAddress, recipient -> recipient.setRegistered(RegisteredState.NOT_REGISTERED));
    }
  }

  public List<Address> getRegistered() {
    SQLiteDatabase db      = databaseHelper.getReadableDatabase();
    List<Address>  results = new LinkedList<>();

    try (Cursor cursor = db.query(TABLE_NAME, new String[] {ADDRESS}, REGISTERED + " = ?", new String[] {"1"}, null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        results.add(Address.fromSerialized(cursor.getString(0)));
      }
    }

    return results;
  }

  public List<Address> getSystemContacts() {
    SQLiteDatabase db      = databaseHelper.getReadableDatabase();
    List<Address>  results = new LinkedList<>();

    try (Cursor cursor = db.query(TABLE_NAME, new String[] {ADDRESS}, SYSTEM_DISPLAY_NAME + " IS NOT NULL AND " + SYSTEM_DISPLAY_NAME + " != \"\"", null, null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        results.add(Address.fromSerialized(cursor.getString(0)));
      }
    }

    return results;
  }

  // XXX This shouldn't be here, and is just a temporary workaround
  public RegisteredState isRegistered(@NonNull Address address) {
    SQLiteDatabase db = databaseHelper.getReadableDatabase();

    try (Cursor cursor = db.query(TABLE_NAME, new String[] {REGISTERED}, ADDRESS + " = ?", new String[] {address.serialize()}, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) return RegisteredState.fromId(cursor.getInt(0));
      else                                        return RegisteredState.UNKNOWN;
    }
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

  public class BulkOperationsHandle {

    private final SQLiteDatabase database;

    private final Map<Address, PendingContactInfo> pendingContactInfoMap = new HashMap<>();

    BulkOperationsHandle(SQLiteDatabase database) {
      this.database = database;
    }

    public void setSystemContactInfo(@NonNull Address address, @Nullable String displayName, @Nullable String photoUri, @Nullable String systemPhoneLabel, @Nullable String systemContactUri) {
      ContentValues contentValues = new ContentValues(1);
      contentValues.put(SYSTEM_DISPLAY_NAME, displayName);
      contentValues.put(SYSTEM_PHOTO_URI, photoUri);
      contentValues.put(SYSTEM_PHONE_LABEL, systemPhoneLabel);
      contentValues.put(SYSTEM_CONTACT_URI, systemContactUri);

      updateOrInsert(address, contentValues);
      pendingContactInfoMap.put(address, new PendingContactInfo(displayName, photoUri, systemPhoneLabel, systemContactUri));
    }

    public void finish() {
      database.setTransactionSuccessful();
      database.endTransaction();

      Stream.of(pendingContactInfoMap.entrySet())
            .forEach(entry -> Recipient.applyCached(entry.getKey(), recipient -> {
              recipient.setName(entry.getValue().displayName);
              recipient.setSystemContactPhoto(Util.uri(entry.getValue().photoUri));
              recipient.setCustomLabel(entry.getValue().phoneLabel);
              recipient.setContactUri(Util.uri(entry.getValue().contactUri));
            }));
    }
  }

  public static class RecipientSettings {
    private final boolean         blocked;
    private final long            muteUntil;
    private final VibrateState    messageVibrateState;
    private final VibrateState    callVibrateState;
    private final Uri             messageRingtone;
    private final Uri             callRingtone;
    private final MaterialColor   color;
    private final boolean         seenInviteReminder;
    private final int             defaultSubscriptionId;
    private final int             expireMessages;
    private final RegisteredState registered;
    private final byte[]          profileKey;
    private final String          systemDisplayName;
    private final String          systemContactPhoto;
    private final String          systemPhoneLabel;
    private final String          systemContactUri;
    private final String          signalProfileName;
    private final String          signalProfileAvatar;
    private final boolean         profileSharing;
    private final String          notificationChannel;

    RecipientSettings(boolean blocked, long muteUntil,
                      @NonNull VibrateState messageVibrateState,
                      @NonNull VibrateState callVibrateState,
                      @Nullable Uri messageRingtone,
                      @Nullable Uri callRingtone,
                      @Nullable MaterialColor color,
                      boolean seenInviteReminder,
                      int defaultSubscriptionId,
                      int expireMessages,
                      @NonNull  RegisteredState registered,
                      @Nullable byte[] profileKey,
                      @Nullable String systemDisplayName,
                      @Nullable String systemContactPhoto,
                      @Nullable String systemPhoneLabel,
                      @Nullable String systemContactUri,
                      @Nullable String signalProfileName,
                      @Nullable String signalProfileAvatar,
                      boolean profileSharing,
                      @Nullable String notificationChannel)
    {
      this.blocked               = blocked;
      this.muteUntil             = muteUntil;
      this.messageVibrateState   = messageVibrateState;
      this.callVibrateState      = callVibrateState;
      this.messageRingtone       = messageRingtone;
      this.callRingtone          = callRingtone;
      this.color                 = color;
      this.seenInviteReminder    = seenInviteReminder;
      this.defaultSubscriptionId = defaultSubscriptionId;
      this.expireMessages        = expireMessages;
      this.registered            = registered;
      this.profileKey            = profileKey;
      this.systemDisplayName     = systemDisplayName;
      this.systemContactPhoto    = systemContactPhoto;
      this.systemPhoneLabel      = systemPhoneLabel;
      this.systemContactUri      = systemContactUri;
      this.signalProfileName     = signalProfileName;
      this.signalProfileAvatar   = signalProfileAvatar;
      this.profileSharing        = profileSharing;
      this.notificationChannel   = notificationChannel;
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

    public boolean hasSeenInviteReminder() {
      return seenInviteReminder;
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

    public byte[] getProfileKey() {
      return profileKey;
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

    public @Nullable String getProfileName() {
      return signalProfileName;
    }

    public @Nullable String getProfileAvatar() {
      return signalProfileAvatar;
    }

    public boolean isProfileSharing() {
      return profileSharing;
    }

    public @Nullable String getNotificationChannel() {
      return notificationChannel;
    }
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
      if (!cursor.moveToNext()) {
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

}
