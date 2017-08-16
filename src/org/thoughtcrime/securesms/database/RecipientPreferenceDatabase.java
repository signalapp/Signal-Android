package org.thoughtcrime.securesms.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.annimon.stream.Stream;

import org.greenrobot.eventbus.EventBus;
import org.thoughtcrime.securesms.color.MaterialColor;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.util.Base64;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class RecipientPreferenceDatabase extends Database {

  private static final String TAG = RecipientPreferenceDatabase.class.getSimpleName();
  private static final String RECIPIENT_PREFERENCES_URI = "content://textsecure/recipients/";

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
  private static final String SIGNAL_PROFILE_NAME     = "signal_profile_name";
  private static final String SIGNAL_PROFILE_AVATAR   = "signal_profile_avatar";

  private static final String[] RECIPIENT_PROJECTION = new String[] {
      BLOCK, NOTIFICATION, VIBRATE, MUTE_UNTIL, COLOR, SEEN_INVITE_REMINDER, DEFAULT_SUBSCRIPTION_ID, EXPIRE_MESSAGES, REGISTERED,
      PROFILE_KEY, SYSTEM_DISPLAY_NAME, SIGNAL_PROFILE_NAME, SIGNAL_PROFILE_AVATAR
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
          PROFILE_KEY + " TEXT DEFAULT NULL, " +
          SIGNAL_PROFILE_NAME + " TEXT DEFAULT NULL, " +
          SIGNAL_PROFILE_AVATAR + " TEXT DEFAULT NULL);";

  public RecipientPreferenceDatabase(Context context, SQLiteOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  public Cursor getBlocked() {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();

    Cursor cursor = database.query(TABLE_NAME, new String[] {ID, ADDRESS}, BLOCK + " = 1",
                                   null, null, null, null, null);
    cursor.setNotificationUri(context.getContentResolver(), Uri.parse(RECIPIENT_PREFERENCES_URI));

    return cursor;
  }

  public BlockedReader readerForBlocked(Cursor cursor) {
    return new BlockedReader(context, cursor);
  }


  public Optional<RecipientsPreferences> getRecipientsPreferences(@NonNull Address address) {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    Cursor         cursor   = null;

    try {
      cursor = database.query(TABLE_NAME, null, ADDRESS + " = ?", new String[] {address.serialize()}, null, null, null);

      if (cursor != null && cursor.moveToNext()) {
        return getRecipientPreferences(cursor);
      }

      return Optional.absent();
    } finally {
      if (cursor != null) cursor.close();
    }
  }

  Optional<RecipientsPreferences> getRecipientPreferences(@NonNull Cursor cursor) {
    boolean blocked               = cursor.getInt(cursor.getColumnIndexOrThrow(BLOCK))                == 1;
    String  notification          = cursor.getString(cursor.getColumnIndexOrThrow(NOTIFICATION));
    int     vibrateState          = cursor.getInt(cursor.getColumnIndexOrThrow(VIBRATE));
    long    muteUntil             = cursor.getLong(cursor.getColumnIndexOrThrow(MUTE_UNTIL));
    String  serializedColor       = cursor.getString(cursor.getColumnIndexOrThrow(COLOR));
    Uri     notificationUri       = notification == null ? null : Uri.parse(notification);
    boolean seenInviteReminder    = cursor.getInt(cursor.getColumnIndexOrThrow(SEEN_INVITE_REMINDER)) == 1;
    int     defaultSubscriptionId = cursor.getInt(cursor.getColumnIndexOrThrow(DEFAULT_SUBSCRIPTION_ID));
    int     expireMessages        = cursor.getInt(cursor.getColumnIndexOrThrow(EXPIRE_MESSAGES));
    boolean registered            = cursor.getInt(cursor.getColumnIndexOrThrow(REGISTERED)) == 1;
    String  profileKeyString      = cursor.getString(cursor.getColumnIndexOrThrow(PROFILE_KEY));
    String  systemDisplayName     = cursor.getString(cursor.getColumnIndexOrThrow(SYSTEM_DISPLAY_NAME));
    String  signalProfileName     = cursor.getString(cursor.getColumnIndexOrThrow(SIGNAL_PROFILE_NAME));
    String  signalProfileAvatar   = cursor.getString(cursor.getColumnIndexOrThrow(SIGNAL_PROFILE_AVATAR));

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

    return Optional.of(new RecipientsPreferences(blocked, muteUntil,
                                                 VibrateState.fromId(vibrateState),
                                                 notificationUri, color, seenInviteReminder,
                                                 defaultSubscriptionId, expireMessages, registered,
                                                 profileKey, systemDisplayName, signalProfileName,
                                                 signalProfileAvatar));
  }

  public BulkOperationsHandle resetAllDisplayNames() {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    database.beginTransaction();

    ContentValues contentValues = new ContentValues(1);
    contentValues.put(SYSTEM_DISPLAY_NAME, (String)null);

    database.update(TABLE_NAME, contentValues, null, null);

    return new BulkOperationsHandle(database);
  }

  public void setColor(Recipient recipient, MaterialColor color) {
    ContentValues values = new ContentValues();
    values.put(COLOR, color.serialize());
    updateOrInsert(recipient.getAddress(), values);
  }

  public void setDefaultSubscriptionId(@NonNull Recipient recipient, int defaultSubscriptionId) {
    ContentValues values = new ContentValues();
    values.put(DEFAULT_SUBSCRIPTION_ID, defaultSubscriptionId);
    updateOrInsert(recipient.getAddress(), values);
    EventBus.getDefault().post(new RecipientPreferenceEvent(recipient));
  }

  public void setBlocked(Recipient recipient, boolean blocked) {
    ContentValues values = new ContentValues();
    values.put(BLOCK, blocked ? 1 : 0);
    updateOrInsert(recipient.getAddress(), values);
  }

  public void setRingtone(Recipient recipient, @Nullable Uri notification) {
    ContentValues values = new ContentValues();
    values.put(NOTIFICATION, notification == null ? null : notification.toString());
    updateOrInsert(recipient.getAddress(), values);
  }

  public void setVibrate(Recipient recipient, @NonNull VibrateState enabled) {
    ContentValues values = new ContentValues();
    values.put(VIBRATE, enabled.getId());
    updateOrInsert(recipient.getAddress(), values);
  }

  public void setMuted(Recipient recipient, long until) {
    Log.w(TAG, "Setting muted until: " + until);
    ContentValues values = new ContentValues();
    values.put(MUTE_UNTIL, until);
    updateOrInsert(recipient.getAddress(), values);
  }

  public void setSeenInviteReminder(Recipient recipient, boolean seen) {
    ContentValues values = new ContentValues(1);
    values.put(SEEN_INVITE_REMINDER, seen ? 1 : 0);
    updateOrInsert(recipient.getAddress(), values);
  }

  public void setExpireMessages(Recipient recipient, int expiration) {
    recipient.setExpireMessages(expiration);

    ContentValues values = new ContentValues(1);
    values.put(EXPIRE_MESSAGES, expiration);
    updateOrInsert(recipient.getAddress(), values);
  }

  public void setSystemDisplayName(@NonNull Address address, @Nullable String systemDisplayName) {
    ContentValues values = new ContentValues(1);
    values.put(SYSTEM_DISPLAY_NAME, systemDisplayName);
    updateOrInsert(address, values);
  }

  public void setProfileKey(@NonNull Address address, @Nullable byte[] profileKey) {
    ContentValues values = new ContentValues(1);
    values.put(PROFILE_KEY, profileKey == null ? null : Base64.encodeBytes(profileKey));
    updateOrInsert(address, values);;
  }

  public void setProfileName(@NonNull Address address, @Nullable String profileName) {
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(SIGNAL_PROFILE_NAME, profileName);
    updateOrInsert(address, contentValues);
  }

  public void setProfileAvatar(@NonNull Address address, @Nullable String profileAvatar) {
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(SIGNAL_PROFILE_AVATAR, profileAvatar);
    updateOrInsert(address, contentValues);
  }

  public Set<Address> getAllRecipients() {
    SQLiteDatabase db      = databaseHelper.getReadableDatabase();
    Set<Address>   results = new HashSet<>();

    try (Cursor cursor = db.query(TABLE_NAME, new String[] {ADDRESS}, null, null, null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        results.add(Address.fromExternal(context, cursor.getString(0)));
      }
    }

    return results;
  }

  public void setRegistered(@NonNull List<Address> activeAddresses,
                            @NonNull List<Address> inactiveAddresses)
  {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();

    for (Address activeAddress : activeAddresses) {
      ContentValues contentValues = new ContentValues(1);
      contentValues.put(REGISTERED, 1);

      updateOrInsert(activeAddress, contentValues);
    }

    for (Address inactiveAddress : inactiveAddresses) {
      ContentValues contentValues = new ContentValues(1);
      contentValues.put(REGISTERED, 0);

      updateOrInsert(inactiveAddress, contentValues);
    }

    context.getContentResolver().notifyChange(Uri.parse(RECIPIENT_PREFERENCES_URI), null);
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

  private void updateOrInsert(Address address, ContentValues contentValues) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();

    database.beginTransaction();

    updateOrInsert(database, address, contentValues);

    database.setTransactionSuccessful();
    database.endTransaction();

    context.getContentResolver().notifyChange(Uri.parse(RECIPIENT_PREFERENCES_URI), null);
  }

  private void updateOrInsert(SQLiteDatabase database, Address address, ContentValues contentValues) {
    int updated = database.update(TABLE_NAME, contentValues, ADDRESS + " = ?",
                                  new String[] {address.serialize()});

    if (updated < 1) {
      contentValues.put(ADDRESS, address.serialize());
      database.insert(TABLE_NAME, null, contentValues);
    }
  }

  public class BulkOperationsHandle {

    private final SQLiteDatabase database;

    public BulkOperationsHandle(SQLiteDatabase database) {
      this.database = database;
    }

    public void setDisplayName(@NonNull Address address, @Nullable String displayName) {
      ContentValues contentValues = new ContentValues(1);
      contentValues.put(SYSTEM_DISPLAY_NAME, displayName);
      updateOrInsert(address, contentValues);
    }

    public void finish() {
      database.setTransactionSuccessful();
      database.endTransaction();
      RecipientFactory.clearCache(context);
      context.getContentResolver().notifyChange(Uri.parse(RECIPIENT_PREFERENCES_URI), null);
    }
  }

  public static class RecipientsPreferences {
    private final boolean       blocked;
    private final long          muteUntil;
    private final VibrateState  vibrateState;
    private final Uri           notification;
    private final MaterialColor color;
    private final boolean       seenInviteReminder;
    private final int           defaultSubscriptionId;
    private final int           expireMessages;
    private final boolean       registered;
    private final byte[]        profileKey;
    private final String        systemDisplayName;
    private final String        signalProfileName;
    private final String        signalProfileAvatar;

    RecipientsPreferences(boolean blocked, long muteUntil,
                          @NonNull VibrateState vibrateState,
                          @Nullable Uri notification,
                          @Nullable MaterialColor color,
                          boolean seenInviteReminder,
                          int defaultSubscriptionId,
                          int expireMessages,
                          boolean registered,
                          @Nullable byte[] profileKey,
                          @Nullable String systemDisplayName,
                          @Nullable String signalProfileName,
                          @Nullable String signalProfileAvatar)
    {
      this.blocked               = blocked;
      this.muteUntil             = muteUntil;
      this.vibrateState          = vibrateState;
      this.notification          = notification;
      this.color                 = color;
      this.seenInviteReminder    = seenInviteReminder;
      this.defaultSubscriptionId = defaultSubscriptionId;
      this.expireMessages        = expireMessages;
      this.registered            = registered;
      this.profileKey            = profileKey;
      this.systemDisplayName     = systemDisplayName;
      this.signalProfileName     = signalProfileName;
      this.signalProfileAvatar   = signalProfileAvatar;
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

    public @NonNull VibrateState getVibrateState() {
      return vibrateState;
    }

    public @Nullable Uri getRingtone() {
      return notification;
    }

    public boolean hasSeenInviteReminder() {
      return seenInviteReminder;
    }

    public Optional<Integer> getDefaultSubscriptionId() {
      return defaultSubscriptionId != -1 ? Optional.of(defaultSubscriptionId) : Optional.<Integer>absent();
    }

    public int getExpireMessages() {
      return expireMessages;
    }

    public boolean isRegistered() {
      return registered;
    }

    public byte[] getProfileKey() {
      return profileKey;
    }

    public @Nullable String getSystemDisplayName() {
      return systemDisplayName;
    }

    public @Nullable String getProfileName() {
      return signalProfileName;
    }

    public @Nullable String getProfileAvatar() {
      return signalProfileAvatar;
    }
  }

  public static class BlockedReader {

    private final Context context;
    private final Cursor cursor;

    BlockedReader(Context context, Cursor cursor) {
      this.context = context;
      this.cursor  = cursor;
    }

    public @NonNull Recipient getCurrent() {
      String serialized = cursor.getString(cursor.getColumnIndexOrThrow(ADDRESS));
      return RecipientFactory.getRecipientFor(context, Address.fromSerialized(serialized), false);
    }

    public @Nullable Recipient getNext() {
      if (!cursor.moveToNext()) {
        return null;
      }

      return getCurrent();
    }
  }

  public static class RecipientPreferenceEvent {

    private final Recipient recipient;

    public RecipientPreferenceEvent(Recipient recipients) {
      this.recipient = recipients;
    }

    public Recipient getRecipient() {
      return recipient;
    }
  }
}
