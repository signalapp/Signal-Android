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

public class DatabaseFactory {

  private static final Object lock = new Object();

  private static DatabaseFactory instance;

  private final SQLCipherOpenHelper   databaseHelper;
  private SmsDatabase           sms;
  private MmsDatabase           mms;
  private AttachmentDatabase    attachments;
  private MediaDatabase         media;
  private ThreadDatabase        thread;
  private MmsSmsDatabase        mmsSmsDatabase;
  private DraftDatabase         draftDatabase;
  private PushDatabase          pushDatabase;
  private GroupDatabase         groupDatabase;
  private RecipientDatabase     recipientDatabase;
  private GroupReceiptDatabase  groupReceiptDatabase;
  private SearchDatabase        searchDatabase;
  private JobDatabase           jobDatabase;
  private LokiAPIDatabase lokiAPIDatabase;
  private LokiMessageDatabase lokiMessageDatabase;
  private LokiThreadDatabase lokiThreadDatabase;
  private LokiUserDatabase lokiUserDatabase;
  private LokiBackupFilesDatabase lokiBackupFilesDatabase;
  private SessionJobDatabase sessionJobDatabase;
  private SessionContactDatabase sessionContactDatabase;
  private Storage storage;
  private DatabaseAttachmentProvider attachmentProvider;

  public static DatabaseFactory getInstance(Context context) {
    synchronized (lock) {
      if (instance == null)
        instance = new DatabaseFactory(context.getApplicationContext());

      return instance;
    }
  }

  public static MmsSmsDatabase getMmsSmsDatabase(Context context) {
    DatabaseFactory factory = getInstance(context);
    synchronized (lock) {
      if (factory.mmsSmsDatabase == null) {
        factory.mmsSmsDatabase = new MmsSmsDatabase(context, factory.databaseHelper);
      }
      return factory.mmsSmsDatabase;
    }
  }

  public static ThreadDatabase getThreadDatabase(Context context) {
    DatabaseFactory factory = getInstance(context);
    synchronized (lock) {
      if (factory.thread == null) {
        factory.thread = new ThreadDatabase(context, factory.databaseHelper);
      }
      return factory.thread;
    }
  }

  public static SmsDatabase getSmsDatabase(Context context) {
    DatabaseFactory factory = getInstance(context);
    synchronized (lock) {
      if (factory.sms == null) {
        factory.sms = new SmsDatabase(context, factory.databaseHelper);
      }
      return factory.sms;
    }
  }

  public static MmsDatabase getMmsDatabase(Context context) {
    DatabaseFactory factory = getInstance(context);
    synchronized (lock) {
      if (factory.mms == null) {
        factory.mms = new MmsDatabase(context, factory.databaseHelper);
      }
      return factory.mms;
    }
  }

  public static AttachmentDatabase getAttachmentDatabase(Context context) {
    DatabaseFactory factory = getInstance(context);
    synchronized (lock) {
      if (factory.attachments == null) {
        AttachmentSecret attachmentSecret = AttachmentSecretProvider.getInstance(context).getOrCreateAttachmentSecret();
        factory.attachments = new AttachmentDatabase(context, factory.databaseHelper, attachmentSecret);
      }
      return factory.attachments;
    }
  }

  public static MediaDatabase getMediaDatabase(Context context) {
    DatabaseFactory factory = getInstance(context);
    synchronized (lock) {
      if (factory.media == null) {
        factory.media = new MediaDatabase(context, factory.databaseHelper);
      }
      return factory.media;
    }
  }

  public static DraftDatabase getDraftDatabase(Context context) {
    DatabaseFactory factory = getInstance(context);
    synchronized (lock) {
      if (factory.draftDatabase == null) {
        factory.draftDatabase = new DraftDatabase(context, factory.databaseHelper);
      }
      return factory.draftDatabase;
    }
  }

  public static PushDatabase getPushDatabase(Context context) {
    DatabaseFactory factory = getInstance(context);
    synchronized (lock) {
      if (factory.pushDatabase == null) {
        factory.pushDatabase = new PushDatabase(context, factory.databaseHelper);
      }
      return factory.pushDatabase;
    }
  }

  public static GroupDatabase getGroupDatabase(Context context) {
    DatabaseFactory factory = getInstance(context);
    synchronized (lock) {
      if (factory.groupDatabase == null) {
        factory.groupDatabase = new GroupDatabase(context, factory.databaseHelper);
      }
      return factory.groupDatabase;
    }
  }

  public static RecipientDatabase getRecipientDatabase(Context context) {
    DatabaseFactory factory = getInstance(context);
    synchronized (lock) {
      if (factory.recipientDatabase == null) {
        factory.recipientDatabase = new RecipientDatabase(context, factory.databaseHelper);
      }
      return factory.recipientDatabase;
    }
  }

  public static GroupReceiptDatabase getGroupReceiptDatabase(Context context) {
    DatabaseFactory factory = getInstance(context);
    synchronized (lock) {
      if (factory.groupReceiptDatabase == null) {
        factory.groupReceiptDatabase = new GroupReceiptDatabase(context, factory.databaseHelper);
      }
      return factory.groupReceiptDatabase;
    }
  }

  public static SearchDatabase getSearchDatabase(Context context) {
    DatabaseFactory factory = getInstance(context);
    synchronized (lock) {
      if (factory.searchDatabase == null) {
        factory.searchDatabase = new SearchDatabase(context, factory.databaseHelper);
      }
      return factory.searchDatabase;
    }
  }

  public static JobDatabase getJobDatabase(Context context) {
    DatabaseFactory factory = getInstance(context);
    synchronized (lock) {
      if (factory.jobDatabase == null) {
        factory.jobDatabase = new JobDatabase(context, factory.databaseHelper);
      }
      return factory.jobDatabase;
    }
  }

  public static SQLiteDatabase getBackupDatabase(Context context) {
    return getInstance(context).databaseHelper.getReadableDatabase();
  }

  // region Loki
  public static LokiAPIDatabase getLokiAPIDatabase(Context context) {
    DatabaseFactory factory = getInstance(context);
    synchronized (lock) {
      if (factory.lokiAPIDatabase == null) {
        factory.lokiAPIDatabase = new LokiAPIDatabase(context, factory.databaseHelper);
      }
      return factory.lokiAPIDatabase;
    }
  }

  public static LokiMessageDatabase getLokiMessageDatabase(Context context) {
    DatabaseFactory factory = getInstance(context);
    synchronized (lock) {
      if (factory.lokiMessageDatabase == null) {
        factory.lokiMessageDatabase = new LokiMessageDatabase(context, factory.databaseHelper);
      }
      return factory.lokiMessageDatabase;
    }
  }

  public static LokiThreadDatabase getLokiThreadDatabase(Context context) {
    DatabaseFactory factory = getInstance(context);
    synchronized (lock) {
      if (factory.lokiThreadDatabase == null) {
        factory.lokiThreadDatabase = new LokiThreadDatabase(context, factory.databaseHelper);
      }
      return factory.lokiThreadDatabase;
    }
  }

  public static LokiUserDatabase getLokiUserDatabase(Context context) {
    DatabaseFactory factory = getInstance(context);
    synchronized (lock) {
      if (factory.lokiUserDatabase == null) {
        factory.lokiUserDatabase = new LokiUserDatabase(context, factory.databaseHelper);
      }
      return factory.lokiUserDatabase;
    }
  }

  public static LokiBackupFilesDatabase getLokiBackupFilesDatabase(Context context) {
    DatabaseFactory factory = getInstance(context);
    synchronized (lock) {
      if (factory.lokiBackupFilesDatabase == null) {
        factory.lokiBackupFilesDatabase = new LokiBackupFilesDatabase(context, factory.databaseHelper);
      }
      return factory.lokiBackupFilesDatabase;
    }
  }

  public static SessionJobDatabase getSessionJobDatabase(Context context) {
    DatabaseFactory factory = getInstance(context);
    synchronized (lock) {
      if (factory.sessionJobDatabase == null) {
        factory.sessionJobDatabase = new SessionJobDatabase(context, factory.databaseHelper);
      }
      return factory.sessionJobDatabase;
    }
  }

  public static SessionContactDatabase getSessionContactDatabase(Context context) {
    DatabaseFactory factory = getInstance(context);
    synchronized (lock) {
      if (factory.sessionContactDatabase == null) {
        factory.sessionContactDatabase = new SessionContactDatabase(context, factory.databaseHelper);
      }
      return factory.sessionContactDatabase;
    }
  }
  // endregion

  // region Refactor
  public static Storage getStorage(Context context) {
    DatabaseFactory factory = getInstance(context);
    synchronized (lock) {
      if (factory.storage == null) {
        factory.storage = new Storage(context, factory.databaseHelper);
      }
      return factory.storage;
    }
  }

  public static DatabaseAttachmentProvider getAttachmentProvider(Context context) {
    DatabaseFactory factory = getInstance(context);
    synchronized (lock) {
      if (factory.attachmentProvider == null) {
        factory.attachmentProvider = new DatabaseAttachmentProvider(context, factory.databaseHelper);
      }
      return factory.attachmentProvider;
    }
  }
  // endregion

  public static void upgradeRestored(Context context, SQLiteDatabase database){
    getInstance(context).databaseHelper.onUpgrade(database, database.getVersion(), -1);
    getInstance(context).databaseHelper.markCurrent(database);
  }

  private DatabaseFactory(@NonNull Context context) {
    SQLiteDatabase.loadLibs(context);

    DatabaseSecret      databaseSecret   = new DatabaseSecretProvider(context).getOrCreateDatabaseSecret();

    this.databaseHelper            = new SQLCipherOpenHelper(context, databaseSecret);
  }

}
