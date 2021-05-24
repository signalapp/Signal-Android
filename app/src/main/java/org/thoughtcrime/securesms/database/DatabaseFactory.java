/*
 * Copyright (C) 2018 Open Whisper Systems
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
import androidx.annotation.NonNull;
import net.sqlcipher.database.SQLiteDatabase;
import org.thoughtcrime.securesms.attachments.DatabaseAttachmentProvider;
import org.thoughtcrime.securesms.crypto.AttachmentSecret;
import org.thoughtcrime.securesms.crypto.AttachmentSecretProvider;
import org.thoughtcrime.securesms.crypto.DatabaseSecret;
import org.thoughtcrime.securesms.crypto.DatabaseSecretProvider;
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;
import org.thoughtcrime.securesms.loki.database.LokiAPIDatabase;
import org.thoughtcrime.securesms.loki.database.LokiBackupFilesDatabase;
import org.thoughtcrime.securesms.loki.database.LokiMessageDatabase;
import org.thoughtcrime.securesms.loki.database.LokiThreadDatabase;
import org.thoughtcrime.securesms.loki.database.LokiUserDatabase;
import org.thoughtcrime.securesms.loki.database.SessionJobDatabase;
import org.thoughtcrime.securesms.loki.database.SessionContactDatabase;

public class DatabaseFactory {

  private static final Object lock = new Object();

  private static DatabaseFactory instance;

  private final SQLCipherOpenHelper   databaseHelper;
  private final SmsDatabase           sms;
  private final MmsDatabase           mms;
  private final AttachmentDatabase    attachments;
  private final MediaDatabase         media;
  private final ThreadDatabase        thread;
  private final MmsSmsDatabase        mmsSmsDatabase;
  private final DraftDatabase         draftDatabase;
  private final PushDatabase          pushDatabase;
  private final GroupDatabase         groupDatabase;
  private final RecipientDatabase     recipientDatabase;
  private final GroupReceiptDatabase  groupReceiptDatabase;
  private final SearchDatabase        searchDatabase;
  private final JobDatabase           jobDatabase;
  private final LokiAPIDatabase lokiAPIDatabase;
  private final LokiMessageDatabase lokiMessageDatabase;
  private final LokiThreadDatabase lokiThreadDatabase;
  private final LokiUserDatabase lokiUserDatabase;
  private final LokiBackupFilesDatabase lokiBackupFilesDatabase;
  private final SessionJobDatabase sessionJobDatabase;
  private final SessionContactDatabase sessionContactDatabase;
  private final Storage storage;
  private final DatabaseAttachmentProvider attachmentProvider;

  public static DatabaseFactory getInstance(Context context) {
    synchronized (lock) {
      if (instance == null)
        instance = new DatabaseFactory(context.getApplicationContext());

      return instance;
    }
  }

  public static MmsSmsDatabase getMmsSmsDatabase(Context context) {
    return getInstance(context).mmsSmsDatabase;
  }

  public static ThreadDatabase getThreadDatabase(Context context) {
    return getInstance(context).thread;
  }

  public static SmsDatabase getSmsDatabase(Context context) {
    return getInstance(context).sms;
  }

  public static MmsDatabase getMmsDatabase(Context context) {
    return getInstance(context).mms;
  }

  public static AttachmentDatabase getAttachmentDatabase(Context context) {
    return getInstance(context).attachments;
  }

  public static MediaDatabase getMediaDatabase(Context context) {
    return getInstance(context).media;
  }

  public static DraftDatabase getDraftDatabase(Context context) {
    return getInstance(context).draftDatabase;
  }

  public static PushDatabase getPushDatabase(Context context) {
    return getInstance(context).pushDatabase;
  }

  public static GroupDatabase getGroupDatabase(Context context) {
    return getInstance(context).groupDatabase;
  }

  public static RecipientDatabase getRecipientDatabase(Context context) {
    return getInstance(context).recipientDatabase;
  }

  public static GroupReceiptDatabase getGroupReceiptDatabase(Context context) {
    return getInstance(context).groupReceiptDatabase;
  }

  public static SearchDatabase getSearchDatabase(Context context) {
    return getInstance(context).searchDatabase;
  }

  public static JobDatabase getJobDatabase(Context context) {
    return getInstance(context).jobDatabase;
  }

  public static SQLiteDatabase getBackupDatabase(Context context) {
    return getInstance(context).databaseHelper.getReadableDatabase();
  }

  // region Loki
  public static LokiAPIDatabase getLokiAPIDatabase(Context context) {
    return getInstance(context).lokiAPIDatabase;
  }

  public static LokiMessageDatabase getLokiMessageDatabase(Context context) {
    return getInstance(context).lokiMessageDatabase;
  }

  public static LokiThreadDatabase getLokiThreadDatabase(Context context) {
    return getInstance(context).lokiThreadDatabase;
  }

  public static LokiUserDatabase getLokiUserDatabase(Context context) {
    return getInstance(context).lokiUserDatabase;
  }

  public static LokiBackupFilesDatabase getLokiBackupFilesDatabase(Context context) {
    return getInstance(context).lokiBackupFilesDatabase;
  }

  public static SessionJobDatabase getSessionJobDatabase(Context context) {
    return getInstance(context).sessionJobDatabase;
  }

  public static SessionContactDatabase getSessionContactDatabase(Context context) {
    return getInstance(context).sessionContactDatabase;
  }
  // endregion

  // region Refactor
  public static Storage getStorage(Context context) {
    return getInstance(context).storage;
  }

  public static DatabaseAttachmentProvider getAttachmentProvider(Context context) {
    return getInstance(context).attachmentProvider;
  }
  // endregion

  public static void upgradeRestored(Context context, SQLiteDatabase database){
    getInstance(context).databaseHelper.onUpgrade(database, database.getVersion(), -1);
    getInstance(context).databaseHelper.markCurrent(database);
  }

  private DatabaseFactory(@NonNull Context context) {
    SQLiteDatabase.loadLibs(context);

    DatabaseSecret      databaseSecret   = new DatabaseSecretProvider(context).getOrCreateDatabaseSecret();
    AttachmentSecret    attachmentSecret = AttachmentSecretProvider.getInstance(context).getOrCreateAttachmentSecret();

    this.databaseHelper            = new SQLCipherOpenHelper(context, databaseSecret);
    this.sms                       = new SmsDatabase(context, databaseHelper);
    this.mms                       = new MmsDatabase(context, databaseHelper);
    this.attachments               = new AttachmentDatabase(context, databaseHelper, attachmentSecret);
    this.media                     = new MediaDatabase(context, databaseHelper);
    this.thread                    = new ThreadDatabase(context, databaseHelper);
    this.mmsSmsDatabase            = new MmsSmsDatabase(context, databaseHelper);
    this.draftDatabase             = new DraftDatabase(context, databaseHelper);
    this.pushDatabase              = new PushDatabase(context, databaseHelper);
    this.groupDatabase             = new GroupDatabase(context, databaseHelper);
    this.recipientDatabase         = new RecipientDatabase(context, databaseHelper);
    this.groupReceiptDatabase      = new GroupReceiptDatabase(context, databaseHelper);
    this.searchDatabase            = new SearchDatabase(context, databaseHelper);
    this.jobDatabase               = new JobDatabase(context, databaseHelper);
    this.lokiAPIDatabase           = new LokiAPIDatabase(context, databaseHelper);
    this.lokiMessageDatabase       = new LokiMessageDatabase(context, databaseHelper);
    this.lokiThreadDatabase        = new LokiThreadDatabase(context, databaseHelper);
    this.lokiUserDatabase          = new LokiUserDatabase(context, databaseHelper);
    this.lokiBackupFilesDatabase   = new LokiBackupFilesDatabase(context, databaseHelper);
    this.storage                   = new Storage(context, databaseHelper);
    this.attachmentProvider        = new DatabaseAttachmentProvider(context, databaseHelper);
    this.sessionJobDatabase        = new SessionJobDatabase(context, databaseHelper);
    this.sessionContactDatabase    = new SessionContactDatabase(context, databaseHelper);
  }

}
