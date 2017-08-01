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

import org.greenrobot.eventbus.EventBus;
import org.thoughtcrime.securesms.color.MaterialColor;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.whispersystems.libsignal.util.guava.Optional;

public class RecipientPreferenceDatabase extends Database {

  private static final String TAG = RecipientPreferenceDatabase.class.getSimpleName();
  private static final String RECIPIENT_PREFERENCES_URI = "content://textsecure/recipients/";

  private static final String TABLE_NAME              = "recipient_preferences";
  private static final String ID                      = "_id";
  private static final String ADDRESS                 = "recipient_ids";
  private static final String BLOCK                   = "block";
  private static final String NOTIFICATION            = "notification";
  private static final String VIBRATE                 = "vibrate";
  private static final String MUTE_UNTIL              = "mute_until";
  private static final String COLOR                   = "color";
  private static final String SEEN_INVITE_REMINDER    = "seen_invite_reminder";
  private static final String DEFAULT_SUBSCRIPTION_ID = "default_subscription_id";
  private static final String EXPIRE_MESSAGES         = "expire_messages";

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
          EXPIRE_MESSAGES + " INTEGER DEFAULT 0);";

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
        boolean blocked               = cursor.getInt(cursor.getColumnIndexOrThrow(BLOCK))                == 1;
        String  notification          = cursor.getString(cursor.getColumnIndexOrThrow(NOTIFICATION));
        int     vibrateState          = cursor.getInt(cursor.getColumnIndexOrThrow(VIBRATE));
        long    muteUntil             = cursor.getLong(cursor.getColumnIndexOrThrow(MUTE_UNTIL));
        String  serializedColor       = cursor.getString(cursor.getColumnIndexOrThrow(COLOR));
        Uri     notificationUri       = notification == null ? null : Uri.parse(notification);
        boolean seenInviteReminder    = cursor.getInt(cursor.getColumnIndexOrThrow(SEEN_INVITE_REMINDER)) == 1;
        int     defaultSubscriptionId = cursor.getInt(cursor.getColumnIndexOrThrow(DEFAULT_SUBSCRIPTION_ID));
        int     expireMessages        = cursor.getInt(cursor.getColumnIndexOrThrow(EXPIRE_MESSAGES));

        MaterialColor color;

        try {
          color = serializedColor == null ? null : MaterialColor.fromSerialized(serializedColor);
        } catch (MaterialColor.UnknownColorException e) {
          Log.w(TAG, e);
          color = null;
        }

        Log.w(TAG, "Muted until: " + muteUntil);

        return Optional.of(new RecipientsPreferences(blocked, muteUntil,
                                                     VibrateState.fromId(vibrateState),
                                                     notificationUri, color, seenInviteReminder,
                                                     defaultSubscriptionId, expireMessages));
      }

      return Optional.absent();
    } finally {
      if (cursor != null) cursor.close();
    }
  }

  public void setColor(Recipient recipient, MaterialColor color) {
    ContentValues values = new ContentValues();
    values.put(COLOR, color.serialize());
    updateOrInsert(recipient, values);
  }

  public void setDefaultSubscriptionId(@NonNull Recipient recipient, int defaultSubscriptionId) {
    ContentValues values = new ContentValues();
    values.put(DEFAULT_SUBSCRIPTION_ID, defaultSubscriptionId);
    updateOrInsert(recipient, values);
    EventBus.getDefault().post(new RecipientPreferenceEvent(recipient));
  }

  public void setBlocked(Recipient recipient, boolean blocked) {
    ContentValues values = new ContentValues();
    values.put(BLOCK, blocked ? 1 : 0);
    updateOrInsert(recipient, values);
  }

  public void setRingtone(Recipient recipient, @Nullable Uri notification) {
    ContentValues values = new ContentValues();
    values.put(NOTIFICATION, notification == null ? null : notification.toString());
    updateOrInsert(recipient, values);
  }

  public void setVibrate(Recipient recipient, @NonNull VibrateState enabled) {
    ContentValues values = new ContentValues();
    values.put(VIBRATE, enabled.getId());
    updateOrInsert(recipient, values);
  }

  public void setMuted(Recipient recipient, long until) {
    Log.w(TAG, "Setting muted until: " + until);
    ContentValues values = new ContentValues();
    values.put(MUTE_UNTIL, until);
    updateOrInsert(recipient, values);
  }

  public void setSeenInviteReminder(Recipient recipient, boolean seen) {
    ContentValues values = new ContentValues(1);
    values.put(SEEN_INVITE_REMINDER, seen ? 1 : 0);
    updateOrInsert(recipient, values);
  }

  public void setExpireMessages(Recipient recipient, int expiration) {
    recipient.setExpireMessages(expiration);

    ContentValues values = new ContentValues(1);
    values.put(EXPIRE_MESSAGES, expiration);
    updateOrInsert(recipient, values);
  }

  private void updateOrInsert(Recipient recipient, ContentValues contentValues) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();

    database.beginTransaction();

    int updated = database.update(TABLE_NAME, contentValues, ADDRESS + " = ?",
                                  new String[] {recipient.getAddress().serialize()});

    if (updated < 1) {
      contentValues.put(ADDRESS, recipient.getAddress().serialize());
      database.insert(TABLE_NAME, null, contentValues);
    }

    database.setTransactionSuccessful();
    database.endTransaction();

    context.getContentResolver().notifyChange(Uri.parse(RECIPIENT_PREFERENCES_URI), null);
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

    RecipientsPreferences(boolean blocked, long muteUntil,
                          @NonNull VibrateState vibrateState,
                          @Nullable Uri notification,
                          @Nullable MaterialColor color,
                          boolean seenInviteReminder,
                          int defaultSubscriptionId,
                          int expireMessages)
    {
      this.blocked               = blocked;
      this.muteUntil             = muteUntil;
      this.vibrateState          = vibrateState;
      this.notification          = notification;
      this.color                 = color;
      this.seenInviteReminder    = seenInviteReminder;
      this.defaultSubscriptionId = defaultSubscriptionId;
      this.expireMessages        = expireMessages;
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
