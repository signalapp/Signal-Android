package org.thoughtcrime.securesms.database.helpers;

import android.database.Cursor;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.SQLiteDatabase;
import org.thoughtcrime.securesms.util.DelimiterUtil;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class RecipientIdCleanupHelper {

  private static final String TAG = Log.tag(RecipientIdCleanupHelper.class);

  public static void execute(@NonNull SQLiteDatabase db) {
    Log.i(TAG, "Beginning migration.");

    long        startTime          = System.currentTimeMillis();
    Pattern     pattern            = Pattern.compile("^[0-9\\-+]+$");
    Set<String> deletionCandidates = new HashSet<>();

    try (Cursor cursor = db.query("recipient", new String[] { "_id", "phone" }, "group_id IS NULL AND email IS NULL", null, null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        String id    = cursor.getString(cursor.getColumnIndexOrThrow("_id"));
        String phone = cursor.getString(cursor.getColumnIndexOrThrow("phone"));

        if (TextUtils.isEmpty(phone) || !pattern.matcher(phone).matches()) {
          Log.i(TAG, "Recipient ID " + id + " has non-numeric characters and can potentially be deleted.");

          if (!isIdUsed(db, "identities", "address", id)     &&
              !isIdUsed(db, "sessions", "address", id)       &&
              !isIdUsed(db, "thread", "recipient_ids", id)   &&
              !isIdUsed(db, "sms", "address", id)            &&
              !isIdUsed(db, "mms", "address", id)            &&
              !isIdUsed(db, "mms", "quote_author", id)       &&
              !isIdUsed(db, "group_receipts", "address", id) &&
              !isIdUsed(db, "groups", "recipient_id", id))
          {
            Log.i(TAG, "Determined ID " + id + " is unused in non-group membership. Marking for potential deletion.");
            deletionCandidates.add(id);
          } else {
            Log.i(TAG, "Found that ID " + id + " is actually used in another table.");
          }
        }
      }
    }

    Set<String> deletions = findUnusedInGroupMembership(db, deletionCandidates);

    for (String deletion : deletions) {
      Log.i(TAG, "Deleting ID " + deletion);
      db.delete("recipient", "_id = ?", new String[] { String.valueOf(deletion) });
    }

    Log.i(TAG, "Migration took " + (System.currentTimeMillis() - startTime) + " ms.");
  }

  private static boolean isIdUsed(@NonNull SQLiteDatabase db, @NonNull String tableName, @NonNull String columnName, String id) {
    try (Cursor cursor = db.query(tableName, new String[] { columnName }, columnName + " = ?", new String[] { id }, null, null, null, "1")) {
      boolean used = cursor != null && cursor.moveToFirst();
      if (used) {
        Log.i(TAG, "Recipient " + id + " was used in (" + tableName + ", " + columnName + ")");
      }
      return used;
    }
  }

  private static Set<String> findUnusedInGroupMembership(@NonNull SQLiteDatabase db, Set<String> candidates) {
    Set<String> unused = new HashSet<>(candidates);

    try (Cursor cursor = db.rawQuery("SELECT members FROM groups", null)) {
      while (cursor != null && cursor.moveToNext()) {
        String   serializedMembers = cursor.getString(cursor.getColumnIndexOrThrow("members"));
        String[] members           = DelimiterUtil.split(serializedMembers, ',');

        for (String member : members) {
          if (unused.remove(member)) {
            Log.i(TAG, "Recipient " + member + " was found in a group membership list.");
          }
        }
      }
    }

    return unused;
  }
}
