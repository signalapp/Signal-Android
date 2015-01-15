package org.thoughtcrime.securesms.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;
import android.util.Log;

import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatchList;
import org.thoughtcrime.securesms.util.JsonUtils;
import org.whispersystems.libaxolotl.IdentityKey;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public abstract class MessagingDatabase extends Database implements MmsSmsColumns {

  private static final String TAG = MessagingDatabase.class.getSimpleName();

  public MessagingDatabase(Context context, SQLiteOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  protected abstract String getTableName();

  public void addMismatchedIdentity(long messageId, long recipientId, IdentityKey identityKey) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    database.beginTransaction();

    try {
      List<IdentityKeyMismatch> mismatches = getMismatchedIdentities(database, messageId);
      mismatches.add(new IdentityKeyMismatch(recipientId, identityKey));

      setMismatchedIdentities(database, messageId, mismatches);
      database.setTransactionSuccessful();
    } catch (IOException ioe) {
      Log.w(TAG, ioe);
    } finally {
      database.endTransaction();
    }
  }

  public void removeMismatchedIdentity(long messageId, long recipientId, IdentityKey identityKey) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    database.beginTransaction();

    try {
      List<IdentityKeyMismatch>     mismatches = getMismatchedIdentities(database, messageId);
      Iterator<IdentityKeyMismatch> iterator   = mismatches.iterator();

      while (iterator.hasNext()) {
        IdentityKeyMismatch mismatch = iterator.next();

        if (mismatch.getRecipientId() == recipientId &&
            mismatch.getIdentityKey().equals(identityKey))
        {
          iterator.remove();
          break;
        }
      }

      setMismatchedIdentities(database, messageId, mismatches);
      database.setTransactionSuccessful();
    } catch (IOException e) {
      Log.w(TAG, e);
    } finally {
      database.endTransaction();
    }
  }

  public List<IdentityKeyMismatch> getMismatchedIdentities(long messageId) {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    return getMismatchedIdentities(database, messageId);
  }

  private void setMismatchedIdentities(SQLiteDatabase database, long messageId, List<IdentityKeyMismatch> mismatches) throws IOException {
    ContentValues contentValues = new ContentValues();

    if (mismatches == null || mismatches.isEmpty()) {
      contentValues.put(MISMATCHED_IDENTITIES, (String)null);
    } else {
      contentValues.put(MISMATCHED_IDENTITIES, JsonUtils.toJson(new IdentityKeyMismatchList(mismatches)));
    }

    database.update(getTableName(), contentValues, ID_WHERE, new String[] {String.valueOf(messageId)});
  }


  private List<IdentityKeyMismatch> getMismatchedIdentities(SQLiteDatabase database, long messageId) {
    Cursor cursor = null;

    try {
      cursor = database.query(getTableName(), new String[] {MISMATCHED_IDENTITIES},
                              ID_WHERE, new String[] {String.valueOf(messageId)},
                              null, null, null);

      if (cursor != null && cursor.moveToNext()) {
        try {
          String document = cursor.getString(cursor.getColumnIndexOrThrow(MISMATCHED_IDENTITIES));

          if (!TextUtils.isEmpty(document)) {
            IdentityKeyMismatchList mismatchList = JsonUtils.fromJson(document, IdentityKeyMismatchList.class);
            return mismatchList.getMismatches();
          }
        } catch (IOException e) {
          Log.w(TAG, e);
        }
      }

      return new LinkedList<>();
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }


}
