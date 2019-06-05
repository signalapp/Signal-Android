package org.thoughtcrime.securesms.database.helpers;


import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import android.text.TextUtils;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteDatabaseHook;
import net.sqlcipher.database.SQLiteOpenHelper;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.crypto.DatabaseSecret;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.database.DraftDatabase;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.GroupReceiptDatabase;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.JobDatabase;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.OneTimePreKeyDatabase;
import org.thoughtcrime.securesms.database.PushDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.SearchDatabase;
import org.thoughtcrime.securesms.database.SessionDatabase;
import org.thoughtcrime.securesms.database.SignedPreKeyDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.StickerDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.jobs.RefreshPreKeysJob;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.io.File;

public class SQLCipherOpenHelper extends SQLiteOpenHelper {

  @SuppressWarnings("unused")
  private static final String TAG = SQLCipherOpenHelper.class.getSimpleName();

  private static final int RECIPIENT_CALL_RINGTONE_VERSION  = 2;
  private static final int MIGRATE_PREKEYS_VERSION          = 3;
  private static final int MIGRATE_SESSIONS_VERSION         = 4;
  private static final int NO_MORE_IMAGE_THUMBNAILS_VERSION = 5;
  private static final int ATTACHMENT_DIMENSIONS            = 6;
  private static final int QUOTED_REPLIES                   = 7;
  private static final int SHARED_CONTACTS                  = 8;
  private static final int FULL_TEXT_SEARCH                 = 9;
  private static final int BAD_IMPORT_CLEANUP               = 10;
  private static final int QUOTE_MISSING                    = 11;
  private static final int NOTIFICATION_CHANNELS            = 12;
  private static final int SECRET_SENDER                    = 13;
  private static final int ATTACHMENT_CAPTIONS              = 14;
  private static final int ATTACHMENT_CAPTIONS_FIX          = 15;
  private static final int PREVIEWS                         = 16;
  private static final int CONVERSATION_SEARCH              = 17;
  private static final int SELF_ATTACHMENT_CLEANUP          = 18;
  private static final int RECIPIENT_FORCE_SMS_SELECTION    = 19;
  private static final int JOBMANAGER_STRIKES_BACK          = 20;
  private static final int STICKERS                         = 21;

  private static final int    DATABASE_VERSION = 21;
  private static final String DATABASE_NAME    = "signal.db";

  private final Context        context;
  private final DatabaseSecret databaseSecret;

  public SQLCipherOpenHelper(@NonNull Context context, @NonNull DatabaseSecret databaseSecret) {
    super(context, DATABASE_NAME, null, DATABASE_VERSION, new SQLiteDatabaseHook() {
      @Override
      public void preKey(SQLiteDatabase db) {
        db.rawExecSQL("PRAGMA cipher_default_kdf_iter = 1;");
        db.rawExecSQL("PRAGMA cipher_default_page_size = 4096;");
      }

      @Override
      public void postKey(SQLiteDatabase db) {
        db.rawExecSQL("PRAGMA kdf_iter = '1';");
        db.rawExecSQL("PRAGMA cipher_page_size = 4096;");
      }
    });

    this.context        = context.getApplicationContext();
    this.databaseSecret = databaseSecret;
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    db.execSQL(SmsDatabase.CREATE_TABLE);
    db.execSQL(MmsDatabase.CREATE_TABLE);
    db.execSQL(AttachmentDatabase.CREATE_TABLE);
    db.execSQL(ThreadDatabase.CREATE_TABLE);
    db.execSQL(IdentityDatabase.CREATE_TABLE);
    db.execSQL(DraftDatabase.CREATE_TABLE);
    db.execSQL(PushDatabase.CREATE_TABLE);
    db.execSQL(GroupDatabase.CREATE_TABLE);
    db.execSQL(RecipientDatabase.CREATE_TABLE);
    db.execSQL(GroupReceiptDatabase.CREATE_TABLE);
    db.execSQL(OneTimePreKeyDatabase.CREATE_TABLE);
    db.execSQL(SignedPreKeyDatabase.CREATE_TABLE);
    db.execSQL(SessionDatabase.CREATE_TABLE);
    db.execSQL(StickerDatabase.CREATE_TABLE);
    executeStatements(db, SearchDatabase.CREATE_TABLE);
    executeStatements(db, JobDatabase.CREATE_TABLE);

    executeStatements(db, SmsDatabase.CREATE_INDEXS);
    executeStatements(db, MmsDatabase.CREATE_INDEXS);
    executeStatements(db, AttachmentDatabase.CREATE_INDEXS);
    executeStatements(db, ThreadDatabase.CREATE_INDEXS);
    executeStatements(db, DraftDatabase.CREATE_INDEXS);
    executeStatements(db, GroupDatabase.CREATE_INDEXS);
    executeStatements(db, GroupReceiptDatabase.CREATE_INDEXES);
    executeStatements(db, StickerDatabase.CREATE_INDEXES);

    if (context.getDatabasePath(ClassicOpenHelper.NAME).exists()) {
      ClassicOpenHelper                      legacyHelper = new ClassicOpenHelper(context);
      android.database.sqlite.SQLiteDatabase legacyDb     = legacyHelper.getWritableDatabase();

      SQLCipherMigrationHelper.migratePlaintext(context, legacyDb, db);

      MasterSecret masterSecret = KeyCachingService.getMasterSecret(context);

      if (masterSecret != null) SQLCipherMigrationHelper.migrateCiphertext(context, masterSecret, legacyDb, db, null);
      else                      TextSecurePreferences.setNeedsSqlCipherMigration(context, true);

      if (!PreKeyMigrationHelper.migratePreKeys(context, db)) {
        ApplicationContext.getInstance(context).getJobManager().add(new RefreshPreKeysJob());
      }

      SessionStoreMigrationHelper.migrateSessions(context, db);
      PreKeyMigrationHelper.cleanUpPreKeys(context);
    }
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    Log.i(TAG, "Upgrading database: " + oldVersion + ", " + newVersion);

    db.beginTransaction();

    try {

      if (oldVersion < RECIPIENT_CALL_RINGTONE_VERSION) {
        db.execSQL("ALTER TABLE recipient_preferences ADD COLUMN call_ringtone TEXT DEFAULT NULL");
        db.execSQL("ALTER TABLE recipient_preferences ADD COLUMN call_vibrate INTEGER DEFAULT " + RecipientDatabase.VibrateState.DEFAULT.getId());
      }

      if (oldVersion < MIGRATE_PREKEYS_VERSION) {
        db.execSQL("CREATE TABLE signed_prekeys (_id INTEGER PRIMARY KEY, key_id INTEGER UNIQUE, public_key TEXT NOT NULL, private_key TEXT NOT NULL, signature TEXT NOT NULL, timestamp INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE one_time_prekeys (_id INTEGER PRIMARY KEY, key_id INTEGER UNIQUE, public_key TEXT NOT NULL, private_key TEXT NOT NULL)");

        if (!PreKeyMigrationHelper.migratePreKeys(context, db)) {
          ApplicationContext.getInstance(context).getJobManager().add(new RefreshPreKeysJob());
        }
      }

      if (oldVersion < MIGRATE_SESSIONS_VERSION) {
        db.execSQL("CREATE TABLE sessions (_id INTEGER PRIMARY KEY, address TEXT NOT NULL, device INTEGER NOT NULL, record BLOB NOT NULL, UNIQUE(address, device) ON CONFLICT REPLACE)");
        SessionStoreMigrationHelper.migrateSessions(context, db);
      }

      if (oldVersion < NO_MORE_IMAGE_THUMBNAILS_VERSION) {
        ContentValues update = new ContentValues();
        update.put("thumbnail", (String)null);
        update.put("aspect_ratio", (String)null);
        update.put("thumbnail_random", (String)null);

        try (Cursor cursor = db.query("part", new String[] {"_id", "ct", "thumbnail"}, "thumbnail IS NOT NULL", null, null, null, null)) {
          while (cursor != null && cursor.moveToNext()) {
            long   id          = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
            String contentType = cursor.getString(cursor.getColumnIndexOrThrow("ct"));

            if (contentType != null && !contentType.startsWith("video")) {
              String thumbnailPath = cursor.getString(cursor.getColumnIndexOrThrow("thumbnail"));
              File   thumbnailFile = new File(thumbnailPath);
              thumbnailFile.delete();

              db.update("part", update, "_id = ?", new String[] {String.valueOf(id)});
            }
          }
        }
      }

      if (oldVersion < ATTACHMENT_DIMENSIONS) {
        db.execSQL("ALTER TABLE part ADD COLUMN width INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE part ADD COLUMN height INTEGER DEFAULT 0");
      }

      if (oldVersion < QUOTED_REPLIES) {
        db.execSQL("ALTER TABLE mms ADD COLUMN quote_id INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE mms ADD COLUMN quote_author TEXT");
        db.execSQL("ALTER TABLE mms ADD COLUMN quote_body TEXT");
        db.execSQL("ALTER TABLE mms ADD COLUMN quote_attachment INTEGER DEFAULT -1");

        db.execSQL("ALTER TABLE part ADD COLUMN quote INTEGER DEFAULT 0");
      }

      if (oldVersion < SHARED_CONTACTS) {
        db.execSQL("ALTER TABLE mms ADD COLUMN shared_contacts TEXT");
      }

      if (oldVersion < FULL_TEXT_SEARCH) {
        db.execSQL("CREATE VIRTUAL TABLE sms_fts USING fts5(body, content=sms, content_rowid=_id)");
        db.execSQL("CREATE TRIGGER sms_ai AFTER INSERT ON sms BEGIN\n" +
                   "  INSERT INTO sms_fts(rowid, body) VALUES (new._id, new.body);\n" +
                   "END;");
        db.execSQL("CREATE TRIGGER sms_ad AFTER DELETE ON sms BEGIN\n" +
                   "  INSERT INTO sms_fts(sms_fts, rowid, body) VALUES('delete', old._id, old.body);\n" +
                   "END;\n");
        db.execSQL("CREATE TRIGGER sms_au AFTER UPDATE ON sms BEGIN\n" +
                   "  INSERT INTO sms_fts(sms_fts, rowid, body) VALUES('delete', old._id, old.body);\n" +
                   "  INSERT INTO sms_fts(rowid, body) VALUES(new._id, new.body);\n" +
                   "END;");

        db.execSQL("CREATE VIRTUAL TABLE mms_fts USING fts5(body, content=mms, content_rowid=_id)");
        db.execSQL("CREATE TRIGGER mms_ai AFTER INSERT ON mms BEGIN\n" +
                   "  INSERT INTO mms_fts(rowid, body) VALUES (new._id, new.body);\n" +
                   "END;");
        db.execSQL("CREATE TRIGGER mms_ad AFTER DELETE ON mms BEGIN\n" +
                   "  INSERT INTO mms_fts(mms_fts, rowid, body) VALUES('delete', old._id, old.body);\n" +
                   "END;\n");
        db.execSQL("CREATE TRIGGER mms_au AFTER UPDATE ON mms BEGIN\n" +
                   "  INSERT INTO mms_fts(mms_fts, rowid, body) VALUES('delete', old._id, old.body);\n" +
                   "  INSERT INTO mms_fts(rowid, body) VALUES(new._id, new.body);\n" +
                   "END;");

        Log.i(TAG, "Beginning to build search index.");
        long start = SystemClock.elapsedRealtime();

        db.execSQL("INSERT INTO sms_fts (rowid, body) SELECT _id, body FROM sms");

        long smsFinished = SystemClock.elapsedRealtime();
        Log.i(TAG, "Indexing SMS completed in " + (smsFinished - start) + " ms");

        db.execSQL("INSERT INTO mms_fts (rowid, body) SELECT _id, body FROM mms");

        long mmsFinished = SystemClock.elapsedRealtime();
        Log.i(TAG, "Indexing MMS completed in " + (mmsFinished - smsFinished) + " ms");
        Log.i(TAG, "Indexing finished. Total time: " + (mmsFinished - start) + " ms");
      }

      if (oldVersion < BAD_IMPORT_CLEANUP) {
        String trimmedCondition = " NOT IN (SELECT _id FROM mms)";

        db.delete("group_receipts", "mms_id" + trimmedCondition, null);

        String[] columns = new String[] { "_id", "unique_id", "_data", "thumbnail"};

        try (Cursor cursor = db.query("part", columns, "mid" + trimmedCondition, null, null, null, null)) {
          while (cursor != null && cursor.moveToNext()) {
            db.delete("part", "_id = ? AND unique_id = ?", new String[] { String.valueOf(cursor.getLong(0)), String.valueOf(cursor.getLong(1)) });

            String data      = cursor.getString(2);
            String thumbnail = cursor.getString(3);

            if (!TextUtils.isEmpty(data)) {
              new File(data).delete();
            }

            if (!TextUtils.isEmpty(thumbnail)) {
              new File(thumbnail).delete();
            }
          }
        }
      }

      // Note: This column only being checked due to upgrade issues as described in #8184
      if (oldVersion < QUOTE_MISSING && !columnExists(db, "mms", "quote_missing")) {
        db.execSQL("ALTER TABLE mms ADD COLUMN quote_missing INTEGER DEFAULT 0");
      }

      // Note: The column only being checked due to upgrade issues as described in #8184
      if (oldVersion < NOTIFICATION_CHANNELS && !columnExists(db, "recipient_preferences", "notification_channel")) {
        db.execSQL("ALTER TABLE recipient_preferences ADD COLUMN notification_channel TEXT DEFAULT NULL");
        NotificationChannels.create(context);

        try (Cursor cursor = db.rawQuery("SELECT recipient_ids, system_display_name, signal_profile_name, notification, vibrate FROM recipient_preferences WHERE notification NOT NULL OR vibrate != 0", null)) {
          while (cursor != null && cursor.moveToNext()) {
            String  addressString   = cursor.getString(cursor.getColumnIndexOrThrow("recipient_ids"));
            Address address         = Address.fromExternal(context, addressString);
            String  systemName      = cursor.getString(cursor.getColumnIndexOrThrow("system_display_name"));
            String  profileName     = cursor.getString(cursor.getColumnIndexOrThrow("signal_profile_name"));
            String  messageSound    = cursor.getString(cursor.getColumnIndexOrThrow("notification"));
            Uri     messageSoundUri = messageSound != null ? Uri.parse(messageSound) : null;
            int     vibrateState    = cursor.getInt(cursor.getColumnIndexOrThrow("vibrate"));
            String  displayName     = NotificationChannels.getChannelDisplayNameFor(context, systemName, profileName, address);
            boolean vibrateEnabled  = vibrateState == 0 ? TextSecurePreferences.isNotificationVibrateEnabled(context) : vibrateState == 1;

            if (address.isGroup()) {
              try(Cursor groupCursor = db.rawQuery("SELECT title FROM groups WHERE group_id = ?", new String[] { address.toGroupString() })) {
                if (groupCursor != null && groupCursor.moveToFirst()) {
                  String title = groupCursor.getString(groupCursor.getColumnIndexOrThrow("title"));

                  if (!TextUtils.isEmpty(title)) {
                    displayName = title;
                  }
                }
              }
            }

            String channelId = NotificationChannels.createChannelFor(context, address, displayName, messageSoundUri, vibrateEnabled);

            ContentValues values = new ContentValues(1);
            values.put("notification_channel", channelId);
            db.update("recipient_preferences", values, "recipient_ids = ?", new String[] { addressString });
          }
        }
      }

      if (oldVersion < SECRET_SENDER) {
        db.execSQL("ALTER TABLE recipient_preferences ADD COLUMN unidentified_access_mode INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE push ADD COLUMN server_timestamp INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE push ADD COLUMN server_guid TEXT DEFAULT NULL");
        db.execSQL("ALTER TABLE group_receipts ADD COLUMN unidentified INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE mms ADD COLUMN unidentified INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE sms ADD COLUMN unidentified INTEGER DEFAULT 0");
      }

      if (oldVersion < ATTACHMENT_CAPTIONS) {
        db.execSQL("ALTER TABLE part ADD COLUMN caption TEXT DEFAULT NULL");
      }

      // 4.30.8 included a migration, but not a correct CREATE_TABLE statement, so we need to add
      // this column if it isn't present.
      if (oldVersion < ATTACHMENT_CAPTIONS_FIX) {
        if (!columnExists(db, "part", "caption")) {
          db.execSQL("ALTER TABLE part ADD COLUMN caption TEXT DEFAULT NULL");
        }
      }

      if (oldVersion < PREVIEWS) {
        db.execSQL("ALTER TABLE mms ADD COLUMN previews TEXT");
      }

      if (oldVersion < CONVERSATION_SEARCH) {
        db.execSQL("DROP TABLE sms_fts");
        db.execSQL("DROP TABLE mms_fts");
        db.execSQL("DROP TRIGGER sms_ai");
        db.execSQL("DROP TRIGGER sms_au");
        db.execSQL("DROP TRIGGER sms_ad");
        db.execSQL("DROP TRIGGER mms_ai");
        db.execSQL("DROP TRIGGER mms_au");
        db.execSQL("DROP TRIGGER mms_ad");

        db.execSQL("CREATE VIRTUAL TABLE sms_fts USING fts5(body, thread_id UNINDEXED, content=sms, content_rowid=_id)");
        db.execSQL("CREATE TRIGGER sms_ai AFTER INSERT ON sms BEGIN\n" +
                   "  INSERT INTO sms_fts(rowid, body, thread_id) VALUES (new._id, new.body, new.thread_id);\n" +
                   "END;");
        db.execSQL("CREATE TRIGGER sms_ad AFTER DELETE ON sms BEGIN\n" +
                   "  INSERT INTO sms_fts(sms_fts, rowid, body, thread_id) VALUES('delete', old._id, old.body, old.thread_id);\n" +
                   "END;\n");
        db.execSQL("CREATE TRIGGER sms_au AFTER UPDATE ON sms BEGIN\n" +
                   "  INSERT INTO sms_fts(sms_fts, rowid, body, thread_id) VALUES('delete', old._id, old.body, old.thread_id);\n" +
                   "  INSERT INTO sms_fts(rowid, body, thread_id) VALUES(new._id, new.body, new.thread_id);\n" +
                   "END;");

        db.execSQL("CREATE VIRTUAL TABLE mms_fts USING fts5(body, thread_id UNINDEXED, content=mms, content_rowid=_id)");
        db.execSQL("CREATE TRIGGER mms_ai AFTER INSERT ON mms BEGIN\n" +
                   "  INSERT INTO mms_fts(rowid, body, thread_id) VALUES (new._id, new.body, new.thread_id);\n" +
                   "END;");
        db.execSQL("CREATE TRIGGER mms_ad AFTER DELETE ON mms BEGIN\n" +
                   "  INSERT INTO mms_fts(mms_fts, rowid, body, thread_id) VALUES('delete', old._id, old.body, old.thread_id);\n" +
                   "END;\n");
        db.execSQL("CREATE TRIGGER mms_au AFTER UPDATE ON mms BEGIN\n" +
                   "  INSERT INTO mms_fts(mms_fts, rowid, body, thread_id) VALUES('delete', old._id, old.body, old.thread_id);\n" +
                   "  INSERT INTO mms_fts(rowid, body, thread_id) VALUES(new._id, new.body, new.thread_id);\n" +
                   "END;");

        Log.i(TAG, "Beginning to build search index.");
        long start = SystemClock.elapsedRealtime();

        db.execSQL("INSERT INTO sms_fts (rowid, body, thread_id) SELECT _id, body, thread_id FROM sms");

        long smsFinished = SystemClock.elapsedRealtime();
        Log.i(TAG, "Indexing SMS completed in " + (smsFinished - start) + " ms");

        db.execSQL("INSERT INTO mms_fts (rowid, body, thread_id) SELECT _id, body, thread_id FROM mms");

        long mmsFinished = SystemClock.elapsedRealtime();
        Log.i(TAG, "Indexing MMS completed in " + (mmsFinished - smsFinished) + " ms");
        Log.i(TAG, "Indexing finished. Total time: " + (mmsFinished - start) + " ms");
      }

      if (oldVersion < SELF_ATTACHMENT_CLEANUP) {
        String localNumber = TextSecurePreferences.getLocalNumber(context);

        if (!TextUtils.isEmpty(localNumber)) {
          try (Cursor threadCursor = db.rawQuery("SELECT _id FROM thread WHERE recipient_ids = ?", new String[]{ localNumber })) {
            if (threadCursor != null && threadCursor.moveToFirst()) {
              long          threadId     = threadCursor.getLong(0);
              ContentValues updateValues = new ContentValues(1);

              updateValues.put("pending_push", 0);

              int count = db.update("part", updateValues, "mid IN (SELECT _id FROM mms WHERE thread_id = ?)", new String[]{ String.valueOf(threadId) });
              Log.i(TAG, "Updated " + count + " self-sent attachments.");
            }
          }
        }
      }

      if (oldVersion < RECIPIENT_FORCE_SMS_SELECTION) {
        db.execSQL("ALTER TABLE recipient_preferences ADD COLUMN force_sms_selection INTEGER DEFAULT 0");
      }

      if (oldVersion < JOBMANAGER_STRIKES_BACK) {
        db.execSQL("CREATE TABLE job_spec(_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                         "job_spec_id TEXT UNIQUE, " +
                                         "factory_key TEXT, " +
                                         "queue_key TEXT, " +
                                         "create_time INTEGER, " +
                                         "next_run_attempt_time INTEGER, " +
                                         "run_attempt INTEGER, " +
                                         "max_attempts INTEGER, " +
                                         "max_backoff INTEGER, " +
                                         "max_instances INTEGER, " +
                                         "lifespan INTEGER, " +
                                         "serialized_data TEXT, " +
                                         "is_running INTEGER)");

        db.execSQL("CREATE TABLE constraint_spec(_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                                "job_spec_id TEXT, " +
                                                "factory_key TEXT, " +
                                                "UNIQUE(job_spec_id, factory_key))");

        db.execSQL("CREATE TABLE dependency_spec(_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                                "job_spec_id TEXT, " +
                                                "depends_on_job_spec_id TEXT, " +
                                                "UNIQUE(job_spec_id, depends_on_job_spec_id))");
      }

      if (oldVersion < STICKERS) {
        db.execSQL("CREATE TABLE sticker (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                         "pack_id TEXT NOT NULL, " +
                                         "pack_key TEXT NOT NULL, " +
                                         "pack_title TEXT NOT NULL, " +
                                         "pack_author TEXT NOT NULL, " +
                                         "sticker_id INTEGER, " +
                                         "cover INTEGER, " +
                                         "emoji TEXT NOT NULL, " +
                                         "last_used INTEGER, " +
                                         "installed INTEGER," +
                                         "file_path TEXT NOT NULL, " +
                                         "file_length INTEGER, " +
                                         "file_random BLOB, " +
                                         "UNIQUE(pack_id, sticker_id, cover) ON CONFLICT IGNORE)");

        db.execSQL("CREATE INDEX IF NOT EXISTS sticker_pack_id_index ON sticker (pack_id);");
        db.execSQL("CREATE INDEX IF NOT EXISTS sticker_sticker_id_index ON sticker (sticker_id);");

        db.execSQL("ALTER TABLE part ADD COLUMN sticker_pack_id TEXT");
        db.execSQL("ALTER TABLE part ADD COLUMN sticker_pack_key TEXT");
        db.execSQL("ALTER TABLE part ADD COLUMN sticker_id INTEGER DEFAULT -1");
        db.execSQL("CREATE INDEX IF NOT EXISTS part_sticker_pack_id_index ON part (sticker_pack_id)");
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }

    if (oldVersion < MIGRATE_PREKEYS_VERSION) {
      PreKeyMigrationHelper.cleanUpPreKeys(context);
    }
  }

  public SQLiteDatabase getReadableDatabase() {
    return getReadableDatabase(databaseSecret.asString());
  }

  public SQLiteDatabase getWritableDatabase() {
    return getWritableDatabase(databaseSecret.asString());
  }

  public void markCurrent(SQLiteDatabase db) {
    db.setVersion(DATABASE_VERSION);
  }

  private void executeStatements(SQLiteDatabase db, String[] statements) {
    for (String statement : statements)
      db.execSQL(statement);
  }

  private static boolean columnExists(@NonNull SQLiteDatabase db, @NonNull String table, @NonNull String column) {
    try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
      int nameColumnIndex = cursor.getColumnIndexOrThrow("name");

      while (cursor.moveToNext()) {
        String name = cursor.getString(nameColumnIndex);

        if (name.equals(column)) {
          return true;
        }
      }
    }

    return false;
  }
}
