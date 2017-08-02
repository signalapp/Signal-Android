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
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Arrays;
import java.util.List;

public class RecipientPreferenceDatabase extends Database {

  private static final String TAG = RecipientPreferenceDatabase.class.getSimpleName();
  private static final String RECIPIENT_PREFERENCES_URI = "content://textsecure/recipients/";

  private static final String TABLE_NAME              = "recipient_preferences";
  private static final String ID                      = "_id";
  private static final String ADDRESSES               = "recipient_ids";
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
          ADDRESSES + " TEXT UNIQUE, " +
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

    Cursor cursor = database.query(TABLE_NAME, new String[] {ID, ADDRESSES}, BLOCK + " = 1",
                                   null, null, null, null, null);
    cursor.setNotificationUri(context.getContentResolver(), Uri.parse(RECIPIENT_PREFERENCES_URI));

    return cursor;
  }

  public BlockedReader readerForBlocked(Cursor cursor) {
    return new BlockedReader(context, cursor);
  }

  public Optional<RecipientsPreferences> getRecipientsPreferences(@NonNull Address[] addresses) {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    Cursor         cursor   = null;

    try {
      cursor = database.query(TABLE_NAME, null, ADDRESSES + " = ?",
                              new String[] {Address.toSerializedList(Arrays.asList(addresses), ' ')},
                              null, null, null);

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

  public void setColor(Recipients recipients, MaterialColor color) {
    ContentValues values = new ContentValues();
    values.put(COLOR, color.serialize());
    updateOrInsert(recipients, values);
  }

  public void setDefaultSubscriptionId(@NonNull  Recipients recipients, int defaultSubscriptionId) {
    ContentValues values = new ContentValues();
    values.put(DEFAULT_SUBSCRIPTION_ID, defaultSubscriptionId);
    updateOrInsert(recipients, values);
    EventBus.getDefault().post(new RecipientPreferenceEvent(recipients));
  }

  public void setBlocked(Recipients recipients, boolean blocked) {
    ContentValues values = new ContentValues();
    values.put(BLOCK, blocked ? 1 : 0);
    updateOrInsert(recipients, values);
  }

  public void setRingtone(Recipients recipients, @Nullable Uri notification) {
    ContentValues values = new ContentValues();
    values.put(NOTIFICATION, notification == null ? null : notification.toString());
    updateOrInsert(recipients, values);
  }

  public void setVibrate(Recipients recipients, @NonNull VibrateState enabled) {
    ContentValues values = new ContentValues();
    values.put(VIBRATE, enabled.getId());
    updateOrInsert(recipients, values);
  }

  public void setMuted(Recipients recipients, long until) {
    Log.w(TAG, "Setting muted until: " + until);
    ContentValues values = new ContentValues();
    values.put(MUTE_UNTIL, until);
    updateOrInsert(recipients, values);
  }

  public void setSeenInviteReminder(Recipients recipients, boolean seen) {
    ContentValues values = new ContentValues(1);
    values.put(SEEN_INVITE_REMINDER, seen ? 1 : 0);
    updateOrInsert(recipients, values);
  }

  public void setExpireMessages(Recipients recipients, int expiration) {
    recipients.setExpireMessages(expiration);

    ContentValues values = new ContentValues(1);
    values.put(EXPIRE_MESSAGES, expiration);
    updateOrInsert(recipients, values);
  }

  private void updateOrInsert(Recipients recipients, ContentValues contentValues) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();

    database.beginTransaction();

    List<Address> addresses           = recipients.getAddressesList();
    String        serializedAddresses = Address.toSerializedList(addresses, ' ');
    int           updated             = database.update(TABLE_NAME, contentValues, ADDRESSES + " = ?", new String[]{serializedAddresses});

    if (updated < 1) {
      contentValues.put(ADDRESSES, serializedAddresses);
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

    public @NonNull Recipients getCurrent() {
      String        serialized  = cursor.getString(cursor.getColumnIndexOrThrow(ADDRESSES));
      List<Address> addressList = Address.fromSerializedList(serialized, ' ');

      return RecipientFactory.getRecipientsFor(context, addressList.toArray(new Address[0]), false);
    }

    public @Nullable Recipients getNext() {
      if (!cursor.moveToNext()) {
        return null;
      }

      return getCurrent();
    }
  }

  public static class RecipientPreferenceEvent {

    private final Recipients recipients;

    RecipientPreferenceEvent(Recipients recipients) {
      this.recipients = recipients;
    }

    public Recipients getRecipients() {
      return recipients;
    }
  }
}
