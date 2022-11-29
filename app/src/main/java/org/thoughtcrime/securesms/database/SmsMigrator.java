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

import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import net.zetetic.database.sqlcipher.SQLiteStatement;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

public class SmsMigrator {

  private static final String TAG = Log.tag(SmsMigrator.class);

  private static class SystemColumns {
    private static final String ADDRESS            = "address";
    private static final String PERSON             = "person";
    private static final String DATE_RECEIVED      = "date";
    private static final String PROTOCOL           = "protocol";
    private static final String READ               = "read";
    private static final String STATUS             = "status";
    private static final String TYPE               = "type";
    private static final String SUBJECT            = "subject";
    private static final String REPLY_PATH_PRESENT = "reply_path_present";
    private static final String BODY               = "body";
    private static final String SERVICE_CENTER     = "service_center";
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

  @SuppressWarnings("SameParameterValue")
  private static void addTranslatedTypeToStatement(SQLiteStatement statement, Cursor cursor, int index, String key)
  {
    int columnIndex = cursor.getColumnIndexOrThrow(key);

    if (cursor.isNull(columnIndex)) {
      statement.bindLong(index, SmsTable.Types.BASE_INBOX_TYPE);
    } else {
      long theirType = cursor.getLong(columnIndex);
      statement.bindLong(index, SmsTable.Types.translateFromSystemBaseType(theirType));
    }
  }

  private static boolean isAppropriateTypeForMigration(Cursor cursor, int columnIndex) {
    long systemType = cursor.getLong(columnIndex);
    long ourType    = SmsTable.Types.translateFromSystemBaseType(systemType);

    return ourType == MmsSmsColumns.Types.BASE_INBOX_TYPE ||
           ourType == MmsSmsColumns.Types.BASE_SENT_TYPE ||
           ourType == MmsSmsColumns.Types.BASE_SENT_FAILED_TYPE;
  }

  private static void getContentValuesForRow(Context context, Cursor cursor, long threadId, SQLiteStatement statement) {
    String      address = cursor.getString(cursor.getColumnIndexOrThrow(SystemColumns.ADDRESS));
    RecipientId id      = Recipient.external(context, address).getId();

    statement.bindString(1, id.serialize());
    addIntToStatement(statement, cursor, 2, SystemColumns.PERSON);
    addIntToStatement(statement, cursor, 3, SystemColumns.DATE_RECEIVED);
    addIntToStatement(statement, cursor, 4, SystemColumns.DATE_RECEIVED);
    addIntToStatement(statement, cursor, 5, SystemColumns.PROTOCOL);
    addIntToStatement(statement, cursor, 6, SystemColumns.READ);
    addIntToStatement(statement, cursor, 7, SystemColumns.STATUS);
    addTranslatedTypeToStatement(statement, cursor, 8, SystemColumns.TYPE);
    addIntToStatement(statement, cursor, 9, SystemColumns.REPLY_PATH_PRESENT);
    addStringToStatement(statement, cursor, 10, SystemColumns.SUBJECT);
    addStringToStatement(statement, cursor, 11, SystemColumns.BODY);
    addStringToStatement(statement, cursor, 12, SystemColumns.SERVICE_CENTER);

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
      Log.w(TAG, iae);
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
        recipientList.add(Recipient.external(context, address));
      }
    }

    if (recipientList.isEmpty()) return null;
    else                         return recipientList;
  }

  private static void migrateConversation(Context context, SmsMigrationProgressListener listener,
                                          ProgressDescription progress,
                                          long theirThreadId, long ourThreadId)
  {
    MessageTable ourSmsDatabase = SignalDatabase.sms();
    Cursor       cursor         = null;
    SQLiteStatement statement      = null;

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
      statement = ourSmsDatabase.createInsertStatement(transaction);

      while (cursor != null && cursor.moveToNext()) {
        int addressColumn = cursor.getColumnIndexOrThrow(SystemColumns.ADDRESS);
        int typeColumn    = cursor.getColumnIndex(SmsTable.TYPE);

        if (!cursor.isNull(addressColumn) && (cursor.isNull(typeColumn) || isAppropriateTypeForMigration(cursor, typeColumn))) {
          getContentValuesForRow(context, cursor, ourThreadId, statement);
          statement.execute();
        }

        listener.progressUpdate(new ProgressDescription(progress, cursor.getCount(), cursor.getPosition()));
      }

      ourSmsDatabase.endTransaction(transaction);
      SignalDatabase.threads().update(ourThreadId, true);
      SignalDatabase.threads().setLastScrolled(ourThreadId, 0);
      SignalDatabase.threads().notifyConversationListeners(ourThreadId);

    } finally {
      if (statement != null)
        statement.close();
      if (cursor != null)
        cursor.close();
    }
  }

  public static void migrateDatabase(Context context, SmsMigrationProgressListener listener)
  {
//    if (context.getSharedPreferences("SecureSMS", Context.MODE_PRIVATE).getBoolean("migrated", false))
//      return;

    ThreadTable threadTable = SignalDatabase.threads();
    Cursor      cursor      = null;

    try {
      Uri threadListUri = Uri.parse("content://mms-sms/conversations?simple=true");
      cursor            = context.getContentResolver().query(threadListUri, null, null, null, "date ASC");

      while (cursor != null && cursor.moveToNext()) {
        long                theirThreadId   = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
        String              theirRecipients = cursor.getString(cursor.getColumnIndexOrThrow("recipient_ids"));
        Set<Recipient>      ourRecipients   = getOurRecipients(context, theirRecipients);
        ProgressDescription progress        = new ProgressDescription(cursor.getCount(), cursor.getPosition(), 100, 0);

        if (ourRecipients != null) {
          if (ourRecipients.size() == 1) {
            long ourThreadId = threadTable.getOrCreateThreadIdFor(ourRecipients.iterator().next());
            migrateConversation(context, listener, progress, theirThreadId, ourThreadId);
          } else if (ourRecipients.size() > 1) {
            ourRecipients.add(Recipient.self());

            List<RecipientId> recipientIds = Stream.of(ourRecipients).map(Recipient::getId).toList();

            GroupId.Mms ourGroupId          = SignalDatabase.groups().getOrCreateMmsGroupForMembers(recipientIds);
            RecipientId ourGroupRecipientId = SignalDatabase.recipients().getOrInsertFromGroupId(ourGroupId);
            Recipient   ourGroupRecipient   = Recipient.resolved(ourGroupRecipientId);
            long        ourThreadId         = threadTable.getOrCreateThreadIdFor(ourGroupRecipient, ThreadTable.DistributionTypes.CONVERSATION);

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
