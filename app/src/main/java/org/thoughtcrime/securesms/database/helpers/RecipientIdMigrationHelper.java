package org.thoughtcrime.securesms.database.helpers;

import android.content.ContentValues;
import android.database.Cursor;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.sqlcipher.database.SQLiteDatabase;

import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.phonenumbers.NumberUtil;
import org.thoughtcrime.securesms.util.DelimiterUtil;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.Util;

import java.util.HashSet;
import java.util.Set;

public class RecipientIdMigrationHelper {

  private static final String TAG = Log.tag(RecipientIdMigrationHelper.class);

  public static void execute(SQLiteDatabase db) {
    Log.i(TAG, "Starting the recipient ID migration.");

    long insertStart = System.currentTimeMillis();

    Log.i(TAG, "Starting inserts for missing recipients.");
    db.execSQL(buildInsertMissingRecipientStatement("identities", "address"));
    db.execSQL(buildInsertMissingRecipientStatement("sessions", "address"));
    db.execSQL(buildInsertMissingRecipientStatement("thread", "recipient_ids"));
    db.execSQL(buildInsertMissingRecipientStatement("sms", "address"));
    db.execSQL(buildInsertMissingRecipientStatement("mms", "address"));
    db.execSQL(buildInsertMissingRecipientStatement("mms", "quote_author"));
    db.execSQL(buildInsertMissingRecipientStatement("group_receipts", "address"));
    db.execSQL(buildInsertMissingRecipientStatement("groups", "group_id"));
    Log.i(TAG, "Finished inserts for missing recipients in " + (System.currentTimeMillis() - insertStart) + " ms.");

    long updateMissingStart = System.currentTimeMillis();

    Log.i(TAG, "Starting updates for invalid or missing addresses.");
    db.execSQL(buildMissingAddressUpdateStatement("sms", "address"));
    db.execSQL(buildMissingAddressUpdateStatement("mms", "address"));
    db.execSQL(buildMissingAddressUpdateStatement("mms", "quote_author"));
    Log.i(TAG, "Finished updates for invalid or missing addresses in " + (System.currentTimeMillis() - updateMissingStart) + " ms.");

    db.execSQL("ALTER TABLE groups ADD COLUMN recipient_id INTEGER DEFAULT 0");

    long updateStart = System.currentTimeMillis();

    Log.i(TAG, "Starting recipient ID updates.");
    db.execSQL(buildUpdateAddressToRecipientIdStatement("identities", "address"));
    db.execSQL(buildUpdateAddressToRecipientIdStatement("sessions", "address"));
    db.execSQL(buildUpdateAddressToRecipientIdStatement("thread", "recipient_ids"));
    db.execSQL(buildUpdateAddressToRecipientIdStatement("sms", "address"));
    db.execSQL(buildUpdateAddressToRecipientIdStatement("mms", "address"));
    db.execSQL(buildUpdateAddressToRecipientIdStatement("mms", "quote_author"));
    db.execSQL(buildUpdateAddressToRecipientIdStatement("group_receipts", "address"));
    db.execSQL("UPDATE groups SET recipient_id = (SELECT _id FROM recipient_preferences WHERE recipient_preferences.recipient_ids = groups.group_id)");
    Log.i(TAG, "Finished recipient ID updates in " + (System.currentTimeMillis() - updateStart) + " ms.");

    // NOTE: Because there's an open cursor on the same table, inserts and updates aren't visible
    //       until afterwards, which is why this group stuff is split into multiple loops

    long findGroupStart = System.currentTimeMillis();

    Log.i(TAG, "Starting to find missing group recipients.");
    Set<String> missingGroupMembers = new HashSet<>();

    try (Cursor cursor = db.rawQuery("SELECT members FROM groups", null)) {
      while (cursor != null && cursor.moveToNext()) {
        String   serializedMembers = cursor.getString(cursor.getColumnIndexOrThrow("members"));
        String[] members           = DelimiterUtil.split(serializedMembers, ',');

        for (String rawMember : members) {
          String member = DelimiterUtil.unescape(rawMember, ',');

          if (!TextUtils.isEmpty(member) && !recipientExists(db, member)) {
            missingGroupMembers.add(member);
          }
        }
      }
    }
    Log.i(TAG, "Finished finding " + missingGroupMembers.size() + " missing group recipients in " + (System.currentTimeMillis() - findGroupStart) + " ms.");

    long insertGroupStart = System.currentTimeMillis();

    Log.i(TAG, "Starting the insert of missing group recipients.");
    for (String member : missingGroupMembers) {
      ContentValues values = new ContentValues();
      values.put("recipient_ids", member);
      db.insert("recipient_preferences", null, values);
    }
    Log.i(TAG, "Finished inserting missing group recipients in " + (System.currentTimeMillis() - insertGroupStart) + " ms.");

    long updateGroupStart = System.currentTimeMillis();

    Log.i(TAG, "Starting group recipient ID updates.");
    try (Cursor cursor = db.rawQuery("SELECT _id, members FROM groups", null)) {
      while (cursor != null && cursor.moveToNext()) {
        long     groupId           = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
        String   serializedMembers = cursor.getString(cursor.getColumnIndexOrThrow("members"));
        String[] members           = DelimiterUtil.split(serializedMembers, ',');
        long[]   memberIds         = new long[members.length];

        for (int i = 0; i < members.length; i++) {
          String member = DelimiterUtil.unescape(members[i], ',');
          memberIds[i] = requireRecipientId(db, member);
        }

        String serializedMemberIds = Util.join(memberIds, ",");

        db.execSQL("UPDATE groups SET members = ? WHERE _id = ?", new String[]{ serializedMemberIds, String.valueOf(groupId) });
      }
    }
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS group_recipient_id_index ON groups (recipient_id)");
    Log.i(TAG, "Finished group recipient ID updates in " + (System.currentTimeMillis() - updateGroupStart) + " ms.");


    long tableCopyStart = System.currentTimeMillis();

    Log.i(TAG, "Starting to copy the recipient table.");
    db.execSQL("CREATE TABLE recipient (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                       "uuid TEXT UNIQUE DEFAULT NULL, " +
                                       "phone TEXT UNIQUE DEFAULT NULL, " +
                                       "email TEXT UNIQUE DEFAULT NULL, " +
                                       "group_id TEXT UNIQUE DEFAULT NULL, " +
                                       "blocked INTEGER DEFAULT 0, " +
                                       "message_ringtone TEXT DEFAULT NULL, " +
                                       "message_vibrate INTEGER DEFAULT 0, " +
                                       "call_ringtone TEXT DEFAULT NULL, " +
                                       "call_vibrate INTEGER DEFAULT 0, " +
                                       "notification_channel TEXT DEFAULT NULL, " +
                                       "mute_until INTEGER DEFAULT 0, " +
                                       "color TEXT DEFAULT NULL, " +
                                       "seen_invite_reminder INTEGER DEFAULT 0, " +
                                       "default_subscription_id INTEGER DEFAULT -1, " +
                                       "message_expiration_time INTEGER DEFAULT 0, " +
                                       "registered INTEGER DEFAULT 0, " +
                                       "system_display_name TEXT DEFAULT NULL, " +
                                       "system_photo_uri TEXT DEFAULT NULL, " +
                                       "system_phone_label TEXT DEFAULT NULL, " +
                                       "system_contact_uri TEXT DEFAULT NULL, " +
                                       "profile_key TEXT DEFAULT NULL, " +
                                       "signal_profile_name TEXT DEFAULT NULL, " +
                                       "signal_profile_avatar TEXT DEFAULT NULL, " +
                                       "profile_sharing INTEGER DEFAULT 0, " +
                                       "unidentified_access_mode INTEGER DEFAULT 0, " +
                                       "force_sms_selection INTEGER DEFAULT 0)");

    try (Cursor cursor = db.query("recipient_preferences", null, null, null, null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        String  address = cursor.getString(cursor.getColumnIndexOrThrow("recipient_ids"));
        boolean isGroup = GroupUtil.isEncodedGroup(address);
        boolean isEmail = !isGroup && NumberUtil.isValidEmail(address);
        boolean isPhone = !isGroup && !isEmail;

        ContentValues values = new ContentValues();

        values.put("_id", cursor.getLong(cursor.getColumnIndexOrThrow("_id")));
        values.put("uuid", (String) null);
        values.put("phone", isPhone ? address : null);
        values.put("email", isEmail ? address : null);
        values.put("group_id", isGroup ? address : null);
        values.put("blocked", cursor.getInt(cursor.getColumnIndexOrThrow("block")));
        values.put("message_ringtone", cursor.getString(cursor.getColumnIndexOrThrow("notification")));
        values.put("message_vibrate", cursor.getString(cursor.getColumnIndexOrThrow("vibrate")));
        values.put("call_ringtone", cursor.getString(cursor.getColumnIndexOrThrow("call_ringtone")));
        values.put("call_vibrate", cursor.getString(cursor.getColumnIndexOrThrow("call_vibrate")));
        values.put("notification_channel", cursor.getString(cursor.getColumnIndexOrThrow("notification_channel")));
        values.put("mute_until", cursor.getLong(cursor.getColumnIndexOrThrow("mute_until")));
        values.put("color", cursor.getString(cursor.getColumnIndexOrThrow("color")));
        values.put("seen_invite_reminder", cursor.getInt(cursor.getColumnIndexOrThrow("seen_invite_reminder")));
        values.put("default_subscription_id", cursor.getInt(cursor.getColumnIndexOrThrow("default_subscription_id")));
        values.put("message_expiration_time", cursor.getInt(cursor.getColumnIndexOrThrow("expire_messages")));
        values.put("registered", cursor.getInt(cursor.getColumnIndexOrThrow("registered")));
        values.put("system_display_name", cursor.getString(cursor.getColumnIndexOrThrow("system_display_name")));
        values.put("system_photo_uri", cursor.getString(cursor.getColumnIndexOrThrow("system_contact_photo")));
        values.put("system_phone_label", cursor.getString(cursor.getColumnIndexOrThrow("system_phone_label")));
        values.put("system_contact_uri", cursor.getString(cursor.getColumnIndexOrThrow("system_contact_uri")));
        values.put("profile_key", cursor.getString(cursor.getColumnIndexOrThrow("profile_key")));
        values.put("signal_profile_name", cursor.getString(cursor.getColumnIndexOrThrow("signal_profile_name")));
        values.put("signal_profile_avatar", cursor.getString(cursor.getColumnIndexOrThrow("signal_profile_avatar")));
        values.put("profile_sharing", cursor.getInt(cursor.getColumnIndexOrThrow("profile_sharing_approval")));
        values.put("unidentified_access_mode", cursor.getInt(cursor.getColumnIndexOrThrow("unidentified_access_mode")));
        values.put("force_sms_selection", cursor.getInt(cursor.getColumnIndexOrThrow("force_sms_selection")));

        db.insert("recipient", null, values);
      }
    }

    db.execSQL("DROP TABLE recipient_preferences");
    Log.i(TAG, "Finished copying the recipient table in " + (System.currentTimeMillis() - tableCopyStart) + " ms.");

    long sanityCheckStart = System.currentTimeMillis();

    Log.i(TAG, "Starting DB integrity sanity checks.");
    assertEmptyQuery(db, "identities", buildSanityCheckQuery("identities", "address"));
    assertEmptyQuery(db, "sessions", buildSanityCheckQuery("sessions", "address"));
    assertEmptyQuery(db, "groups", buildSanityCheckQuery("groups", "recipient_id"));
    assertEmptyQuery(db, "thread", buildSanityCheckQuery("thread", "recipient_ids"));
    assertEmptyQuery(db, "sms", buildSanityCheckQuery("sms", "address"));
    assertEmptyQuery(db, "mms -- address", buildSanityCheckQuery("mms", "address"));
    assertEmptyQuery(db, "mms -- quote_author", buildSanityCheckQuery("mms", "quote_author"));
    assertEmptyQuery(db, "group_receipts", buildSanityCheckQuery("group_receipts", "address"));
    Log.i(TAG, "Finished DB integrity sanity checks in " + (System.currentTimeMillis() - sanityCheckStart) + " ms.");

    Log.i(TAG, "Finished recipient ID migration in " + (System.currentTimeMillis() - insertStart) + " ms.");
  }

  private static String buildUpdateAddressToRecipientIdStatement(@NonNull String table, @NonNull String addressColumn) {
    return "UPDATE " + table + " SET " + addressColumn + "=(SELECT _id " +
                                                           "FROM recipient_preferences " +
                                                           "WHERE recipient_preferences.recipient_ids = " + table + "." + addressColumn + ")";
  }

  private static String buildInsertMissingRecipientStatement(@NonNull String table, @NonNull String addressColumn) {
    return "INSERT INTO recipient_preferences(recipient_ids) SELECT DISTINCT " + addressColumn + " " +
                                                            "FROM " + table + " " +
                                                            "WHERE " + addressColumn + " != '' AND " +
                                                                       addressColumn + " != 'insert-address-column' AND " +
                                                                       addressColumn + " NOT NULL AND " +
                                                                       addressColumn + " NOT IN (SELECT recipient_ids FROM recipient_preferences)";
  }

  private static String buildMissingAddressUpdateStatement(@NonNull String table, @NonNull String addressColumn) {
    return "UPDATE " + table + " SET " + addressColumn + " = -1 " +
           "WHERE " + addressColumn + " = '' OR " +
                      addressColumn + " IS NULL OR " +
                      addressColumn + " = 'insert-address-token'";
  }

  private static boolean recipientExists(@NonNull SQLiteDatabase db, @NonNull String address) {
    return getRecipientId(db, address) != null;
  }

  private static @Nullable Long getRecipientId(@NonNull SQLiteDatabase db, @NonNull String address) {
    try (Cursor cursor = db.rawQuery("SELECT _id FROM recipient_preferences WHERE recipient_ids = ?", new String[]{ address })) {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getLong(cursor.getColumnIndex("_id"));
      } else {
        return null;
      }
    }
  }

  private static long requireRecipientId(@NonNull SQLiteDatabase db, @NonNull String address) {
    Long id = getRecipientId(db, address);

    if (id != null) {
      return id;
    } else {
      throw new MissingRecipientError(address);
    }
  }

  private static String buildSanityCheckQuery(@NonNull String table, @NonNull String idColumn) {
    return "SELECT " + idColumn + " FROM " + table + " WHERE " + idColumn + " != -1 AND " + idColumn + " NOT IN (SELECT _id FROM recipient)";
  }

  private static void assertEmptyQuery(@NonNull SQLiteDatabase db, @NonNull String tag, @NonNull String query) {
    try (Cursor cursor = db.rawQuery(query, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        throw new FailedSanityCheckError(tag);
      }
    }
  }

  private static final class MissingRecipientError extends AssertionError {
    MissingRecipientError(@NonNull String address) {
      super("Could not find recipient with address " + address);
    }
  }

  private static final class FailedSanityCheckError extends AssertionError {
    FailedSanityCheckError(@NonNull String tableName) {
      super("Sanity check failed for tag '" + tableName + "'");
    }
  }
}
