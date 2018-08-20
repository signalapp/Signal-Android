/*
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
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.support.annotation.Nullable;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteStatement;

import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

public class SmsMigrator {

  private static final String TAG = SmsMigrator.class.getSimpleName();

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

  @SuppressWarnings("SameParameterValue")
  private static void addTranslatedTypeToStatement(SQLiteStatement statement, Cursor cursor, int index, String key)
  {
    int columnIndex = cursor.getColumnIndexOrThrow(key);

    if (cursor.isNull(columnIndex)) {
      statement.bindLong(index, SmsDatabase.Types.BASE_INBOX_TYPE);
    } else {
      long theirType = cursor.getLong(columnIndex);
      statement.bindLong(index, SmsDatabase.Types.translateFromSystemBaseType(theirType));
    }
  }

  private static boolean isAppropriateTypeForMigration(Cursor cursor, int columnIndex) {
    long systemType = cursor.getLong(columnIndex);
    long ourType    = SmsDatabase.Types.translateFromSystemBaseType(systemType);

    return ourType == MmsSmsColumns.Types.BASE_INBOX_TYPE ||
           ourType == MmsSmsColumns.Types.BASE_SENT_TYPE ||
           ourType == MmsSmsColumns.Types.BASE_SENT_FAILED_TYPE;
  }

  private static void getContentValuesForRow(Context context, Cursor cursor,
                                             long threadId, SQLiteStatement statement)
  {
    String theirAddress = cursor.getString(cursor.getColumnIndexOrThrow(SmsDatabase.ADDRESS));
    statement.bindString(1, Address.fromExternal(context, theirAddress).serialize());

    addIntToStatement(statement, cursor, 2, SmsDatabase.PERSON);
    addIntToStatement(statement, cursor, 3, SmsDatabase.DATE_RECEIVED);
    addIntToStatement(statement, cursor, 4, SmsDatabase.DATE_RECEIVED);
    addIntToStatement(statement, cursor, 5, SmsDatabase.PROTOCOL);
    addIntToStatement(statement, cursor, 6, SmsDatabase.READ);
    addIntToStatement(statement, cursor, 7, SmsDatabase.STATUS);
    addTranslatedTypeToStatement(statement, cursor, 8, SmsDatabase.TYPE);
    addIntToStatement(statement, cursor, 9, SmsDatabase.REPLY_PATH_PRESENT);
    addStringToStatement(statement, cursor, 10, SmsDatabase.SUBJECT);
    addStringToStatement(statement, cursor, 11, SmsDatabase.BODY);
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

  private static @Nullable Set<Recipient> getOurRecipients(Context context, String theirRecipients) {
    StringTokenizer tokenizer     = new StringTokenizer(theirRecipients.trim(), " ");
    Set<Recipient>  recipientList = new HashSet<>();

    while (tokenizer.hasMoreTokens()) {
      String theirRecipientId = tokenizer.nextToken();
      String address          = getTheirCanonicalAddress(context, theirRecipientId);

      if (address != null) {
        recipientList.add(Recipient.from(context, Address.fromExternal(context, address), true));
      }
    }

    if (recipientList.isEmpty()) return null;
    else                         return recipientList;
  }

  private static void migrateConversation(Context context, SmsMigrationProgressListener listener,
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
          getContentValuesForRow(context, cursor, ourThreadId, statement);
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

  public static void migrateDatabase(Context context, SmsMigrationProgressListener listener)
  {
//    if (context.getSharedPreferences("SecureSMS", Context.MODE_PRIVATE).getBoolean("migrated", false))
//      return;

    ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(context);
    Cursor cursor                 = null;

    try {
      Uri threadListUri = Uri.parse("content://mms-sms/conversations?simple=true");
      cursor            = context.getContentResolver().query(threadListUri, null, null, null, "date ASC");

      while (cursor != null && cursor.moveToNext()) {
        long                theirThreadId   = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
        String              theirRecipients = cursor.getString(cursor.getColumnIndexOrThrow("recipient_ids"));
        Set<Recipient>     ourRecipients   = getOurRecipients(context, theirRecipients);
        ProgressDescription progress        = new ProgressDescription(cursor.getCount(), cursor.getPosition(), 100, 0);

        if (ourRecipients != null) {
          if (ourRecipients.size() == 1) {
            long ourThreadId = threadDatabase.getThreadIdFor(ourRecipients.iterator().next());
            migrateConversation(context, listener, progress, theirThreadId, ourThreadId);
          } else if (ourRecipients.size() > 1) {
            ourRecipients.add(Recipient.from(context, Address.fromSerialized(TextSecurePreferences.getLocalNumber(context)), true));

            List<Address> memberAddresses = new LinkedList<>();

            for (Recipient recipient : ourRecipients) {
              memberAddresses.add(recipient.getAddress());
            }

            String    ourGroupId        = DatabaseFactory.getGroupDatabase(context).getOrCreateGroupForMembers(memberAddresses, true);
            Recipient ourGroupRecipient = Recipient.from(context, Address.fromSerialized(ourGroupId), true);
            long      ourThreadId       = threadDatabase.getThreadIdFor(ourGroupRecipient, ThreadDatabase.DistributionTypes.CONVERSATION);

            migrateConversation(context, listener, progress, theirThreadId, ourThreadId);
          }
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
    void progressUpdate(ProgressDescription description);
  }

  public static class ProgressDescription {
    public final int primaryTotal;
    public       int primaryComplete;
    public final int secondaryTotal;
    public final int secondaryComplete;

    ProgressDescription(int primaryTotal, int primaryComplete,
                        int secondaryTotal, int secondaryComplete)
    {
      this.primaryTotal      = primaryTotal;
      this.primaryComplete   = primaryComplete;
      this.secondaryTotal    = secondaryTotal;
      this.secondaryComplete = secondaryComplete;
    }

    ProgressDescription(ProgressDescription that, int secondaryTotal, int secondaryComplete) {
      this.primaryComplete   = that.primaryComplete;
      this.primaryTotal      = that.primaryTotal;
      this.secondaryComplete = secondaryComplete;
      this.secondaryTotal    = secondaryTotal;
    }

    void incrementPrimaryComplete() {
      primaryComplete += 1;
    }
  }

}
