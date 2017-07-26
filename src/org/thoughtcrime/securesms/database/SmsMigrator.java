/**
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.database;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteStatement;
import android.net.Uri;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.MasterCipher;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;

import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

public class SmsMigrator {

  private static final String TAG = SmsMigrator.class.getSimpleName();

  private static void addEncryptedStringToStatement(Context context, SQLiteStatement statement,
                                                    Cursor cursor, MasterSecret masterSecret,
                                                    int index, String key)
  {
    int columnIndex = cursor.getColumnIndexOrThrow(key);

    if (cursor.isNull(columnIndex)) {
      statement.bindNull(index);
    } else {
      statement.bindString(index, encrypt(masterSecret, cursor.getString(columnIndex)));
    }
  }

  private static void addStringToStatement(SQLiteStatement statement, Cursor cursor,
                                           int index, String key)
  {
    int columnIndex = cursor.getColumnIndexOrThrow(key);

    if (cursor.isNull(columnIndex)) {
      statement.bindNull(index);
    } else {
      statement.bindString(index, cursor.getString(columnIndex));
    }
  }

  private static void addIntToStatement(SQLiteStatement statement, Cursor cursor,
                                        int index, String key)
  {
    int columnIndex = cursor.getColumnIndexOrThrow(key);

    if (cursor.isNull(columnIndex)) {
      statement.bindNull(index);
    } else {
      statement.bindLong(index, cursor.getLong(columnIndex));
    }
  }

  private static void addTranslatedTypeToStatement(SQLiteStatement statement, Cursor cursor,
                                                   int index, String key)
  {
    int columnIndex = cursor.getColumnIndexOrThrow(key);

    if (cursor.isNull(columnIndex)) {
      statement.bindLong(index, SmsDatabase.Types.BASE_INBOX_TYPE | SmsDatabase.Types.ENCRYPTION_SYMMETRIC_BIT);
    } else {
      long theirType = cursor.getLong(columnIndex);
      statement.bindLong(index, SmsDatabase.Types.translateFromSystemBaseType(theirType) | SmsDatabase.Types.ENCRYPTION_SYMMETRIC_BIT);
    }
  }

  private static boolean isAppropriateTypeForMigration(Cursor cursor, int columnIndex) {
    long systemType = cursor.getLong(columnIndex);
    long ourType    = SmsDatabase.Types.translateFromSystemBaseType(systemType);

    return ourType == MmsSmsColumns.Types.BASE_INBOX_TYPE ||
           ourType == MmsSmsColumns.Types.BASE_SENT_TYPE ||
           ourType == MmsSmsColumns.Types.BASE_SENT_FAILED_TYPE;
  }

  private static void getContentValuesForRow(Context context, MasterSecret masterSecret,
                                             Cursor cursor, long threadId,
                                             SQLiteStatement statement)
  {
    addStringToStatement(statement, cursor, 1, SmsDatabase.ADDRESS);
    addIntToStatement(statement, cursor, 2, SmsDatabase.PERSON);
    addIntToStatement(statement, cursor, 3, SmsDatabase.DATE_RECEIVED);
    addIntToStatement(statement, cursor, 4, SmsDatabase.DATE_RECEIVED);
    addIntToStatement(statement, cursor, 5, SmsDatabase.PROTOCOL);
    addIntToStatement(statement, cursor, 6, SmsDatabase.READ);
    addIntToStatement(statement, cursor, 7, SmsDatabase.STATUS);
    addTranslatedTypeToStatement(statement, cursor, 8, SmsDatabase.TYPE);
    addIntToStatement(statement, cursor, 9, SmsDatabase.REPLY_PATH_PRESENT);
    addStringToStatement(statement, cursor, 10, SmsDatabase.SUBJECT);
    addEncryptedStringToStatement(context, statement, cursor, masterSecret, 11, SmsDatabase.BODY);
    addStringToStatement(statement, cursor, 12, SmsDatabase.SERVICE_CENTER);

    statement.bindLong(13, threadId);
  }

  private static String getTheirCanonicalAddress(Context context, String theirRecipientId) {
    Uri uri       = Uri.parse("content://mms-sms/canonical-address/" + theirRecipientId);
    Cursor cursor = null;

    try {
      cursor = context.getContentResolver().query(uri, null, null, null, null);

      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getString(0);
      } else {
        return null;
      }
    } catch (IllegalStateException iae) {
      Log.w("SmsMigrator", iae);
      return null;
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  private static Recipients getOurRecipients(Context context, String theirRecipients) {
    StringTokenizer tokenizer   = new StringTokenizer(theirRecipients.trim(), " ");
    List<Address>   addressList = new LinkedList<>();

    while (tokenizer.hasMoreTokens()) {
      String theirRecipientId = tokenizer.nextToken();
      String address          = getTheirCanonicalAddress(context, theirRecipientId);

      if (address != null) {
        addressList.add(Address.fromExternal(context, address));
      }
    }

    if (addressList.isEmpty()) return null;
    else                       return RecipientFactory.getRecipientsFor(context, addressList.toArray(new Address[0]), true);
  }

  private static String encrypt(MasterSecret masterSecret, String body)
  {
    MasterCipher masterCipher = new MasterCipher(masterSecret);
    return masterCipher.encryptBody(body);
  }

  private static void migrateConversation(Context context, MasterSecret masterSecret,
                                          SmsMigrationProgressListener listener,
                                          ProgressDescription progress,
                                          long theirThreadId, long ourThreadId)
  {
    SmsDatabase ourSmsDatabase = DatabaseFactory.getSmsDatabase(context);
    Cursor cursor              = null;

    try {
      Uri uri = Uri.parse("content://sms/conversations/" + theirThreadId);

      try {
        cursor = context.getContentResolver().query(uri, null, null, null, null);
      } catch (SQLiteException e) {
        /// Work around for weird sony-specific (?) bug: #4309
        Log.w(TAG, e);
        return;
      }

      SQLiteDatabase transaction = ourSmsDatabase.beginTransaction();
      SQLiteStatement statement  = ourSmsDatabase.createInsertStatement(transaction);

      while (cursor != null && cursor.moveToNext()) {
        int typeColumn = cursor.getColumnIndex(SmsDatabase.TYPE);

        if (cursor.isNull(typeColumn) || isAppropriateTypeForMigration(cursor, typeColumn)) {
          getContentValuesForRow(context, masterSecret, cursor, ourThreadId, statement);
          statement.execute();
        }

        listener.progressUpdate(new ProgressDescription(progress, cursor.getCount(), cursor.getPosition()));
      }

      ourSmsDatabase.endTransaction(transaction);
      DatabaseFactory.getThreadDatabase(context).update(ourThreadId, true);
      DatabaseFactory.getThreadDatabase(context).notifyConversationListeners(ourThreadId);

    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public static void migrateDatabase(Context context,
                                     MasterSecret masterSecret,
                                     SmsMigrationProgressListener listener)
  {
//    if (context.getSharedPreferences("SecureSMS", Context.MODE_PRIVATE).getBoolean("migrated", false))
//      return;

    ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(context);
    Cursor cursor                 = null;

    try {
      Uri threadListUri = Uri.parse("content://mms-sms/conversations?simple=true");
      cursor            = context.getContentResolver().query(threadListUri, null, null, null, "date ASC");

      while (cursor != null && cursor.moveToNext()) {
        long   theirThreadId         = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
        String theirRecipients       = cursor.getString(cursor.getColumnIndexOrThrow("recipient_ids"));
        Recipients ourRecipients     = getOurRecipients(context, theirRecipients);
        ProgressDescription progress = new ProgressDescription(cursor.getCount(), cursor.getPosition(), 100, 0);

        if (ourRecipients != null) {
          long ourThreadId = threadDatabase.getThreadIdFor(ourRecipients);
          migrateConversation(context, masterSecret,
                              listener, progress,
                              theirThreadId, ourThreadId);
        }

        progress.incrementPrimaryComplete();
        listener.progressUpdate(progress);
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }

    context.getSharedPreferences("SecureSMS", Context.MODE_PRIVATE).edit()
      .putBoolean("migrated", true).apply();
  }

  public interface SmsMigrationProgressListener {
    public void progressUpdate(ProgressDescription description);
  }

  public static class ProgressDescription {
    public final int primaryTotal;
    public       int primaryComplete;
    public final int secondaryTotal;
    public final int secondaryComplete;

    public ProgressDescription(int primaryTotal, int primaryComplete,
                               int secondaryTotal, int secondaryComplete)
    {
      this.primaryTotal      = primaryTotal;
      this.primaryComplete   = primaryComplete;
      this.secondaryTotal    = secondaryTotal;
      this.secondaryComplete = secondaryComplete;
    }

    public ProgressDescription(ProgressDescription that, int secondaryTotal, int secondaryComplete) {
      this.primaryComplete   = that.primaryComplete;
      this.primaryTotal      = that.primaryTotal;
      this.secondaryComplete = secondaryComplete;
      this.secondaryTotal    = secondaryTotal;
    }

    public void incrementPrimaryComplete() {
      primaryComplete += 1;
    }
  }

}
