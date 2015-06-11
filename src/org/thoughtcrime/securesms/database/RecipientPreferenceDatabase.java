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

import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libaxolotl.util.guava.Optional;

import java.util.Arrays;


public class RecipientPreferenceDatabase extends Database {

  private static final String TAG = RecipientPreferenceDatabase.class.getSimpleName();

  private static final String TABLE_NAME    = "recipient_preferences";
  private static final String ID            = "_id";
  private static final String RECIPIENT_IDS = "recipient_ids";
  private static final String BLOCK         = "block";
  private static final String NOTIFICATION  = "notification";
  private static final String VIBRATE       = "vibrate";
  private static final String MUTE_UNTIL    = "mute_until";

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
          RECIPIENT_IDS + " TEXT UNIQUE, " +
          BLOCK + " INTEGER DEFAULT 0," +
          NOTIFICATION + " TEXT DEFAULT NULL, " +
          VIBRATE + " INTEGER DEFAULT " + VibrateState.DEFAULT.getId() + ", " +
          MUTE_UNTIL + " INTEGER DEFAULT 0);";

  public RecipientPreferenceDatabase(Context context, SQLiteOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  public Cursor getBlocked() {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();

    return database.query(TABLE_NAME, new String[] {ID, RECIPIENT_IDS}, BLOCK + " = 1",
                          null, null, null, null, null);
  }

  public Optional<RecipientsPreferences> getRecipientsPreferences(@NonNull long[] recipients) {
    Arrays.sort(recipients);

    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    Cursor         cursor   = null;

    try {
      cursor = database.query(TABLE_NAME, null, RECIPIENT_IDS + " = ?",
                              new String[] {Util.join(recipients, " ")},
                              null, null, null);

      if (cursor != null && cursor.moveToNext()) {
        boolean blocked         = cursor.getInt(cursor.getColumnIndexOrThrow(BLOCK)) == 1;
        String  notification    = cursor.getString(cursor.getColumnIndexOrThrow(NOTIFICATION));
        int     vibrateState    = cursor.getInt(cursor.getColumnIndexOrThrow(VIBRATE));
        long    muteUntil       = cursor.getLong(cursor.getColumnIndexOrThrow(MUTE_UNTIL));
        Uri     notificationUri = notification == null ? null : Uri.parse(notification);

        Log.w(TAG, "Muted until: " + muteUntil);

        return Optional.of(new RecipientsPreferences(blocked, muteUntil,
                                                     VibrateState.fromId(vibrateState),
                                                     notificationUri));
      }

      return Optional.absent();
    } finally {
      if (cursor != null) cursor.close();
    }
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

  private void updateOrInsert(Recipients recipients, ContentValues contentValues) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();

    database.beginTransaction();

    int updated = database.update(TABLE_NAME, contentValues, RECIPIENT_IDS + " = ?",
                                  new String[] {String.valueOf(recipients.getSortedIdsString())});

    if (updated < 1) {
      contentValues.put(RECIPIENT_IDS, recipients.getSortedIdsString());
      database.insert(TABLE_NAME, null, contentValues);
    }

    database.setTransactionSuccessful();
    database.endTransaction();
  }

  public static class RecipientsPreferences {
    private final boolean      blocked;
    private final long         muteUntil;
    private final VibrateState vibrateState;
    private final Uri          notification;

    public RecipientsPreferences(boolean blocked, long muteUntil, VibrateState vibrateState, Uri notification) {
      this.blocked      = blocked;
      this.muteUntil    = muteUntil;
      this.vibrateState = vibrateState;
      this.notification = notification;
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
  }
}
