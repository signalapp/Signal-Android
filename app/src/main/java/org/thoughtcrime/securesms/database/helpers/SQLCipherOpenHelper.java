package org.thoughtcrime.securesms.database.helpers;


import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;
import com.bumptech.glide.Glide;
import com.google.protobuf.InvalidProtocolBufferException;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteOpenHelper;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.color.MaterialColor;
import org.thoughtcrime.securesms.contacts.avatars.ContactColorsLegacy;
import org.thoughtcrime.securesms.conversation.colors.AvatarColor;
import org.thoughtcrime.securesms.conversation.colors.ChatColors;
import org.thoughtcrime.securesms.conversation.colors.ChatColorsMapper;
import org.thoughtcrime.securesms.crypto.DatabaseSecret;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.database.ChatColorsDatabase;
import org.thoughtcrime.securesms.database.DraftDatabase;
import org.thoughtcrime.securesms.database.EmojiSearchDatabase;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.GroupReceiptDatabase;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.MentionDatabase;
import org.thoughtcrime.securesms.database.MessageSendLogDatabase;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.OneTimePreKeyDatabase;
import org.thoughtcrime.securesms.database.PaymentDatabase;
import org.thoughtcrime.securesms.database.PendingRetryReceiptDatabase;
import org.thoughtcrime.securesms.database.PushDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.RemappedRecordsDatabase;
import org.thoughtcrime.securesms.database.SearchDatabase;
import org.thoughtcrime.securesms.database.SenderKeyDatabase;
import org.thoughtcrime.securesms.database.SenderKeySharedDatabase;
import org.thoughtcrime.securesms.database.SessionDatabase;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.SignedPreKeyDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.SqlCipherDatabaseHook;
import org.thoughtcrime.securesms.database.SqlCipherErrorHandler;
import org.thoughtcrime.securesms.database.StickerDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.UnknownStorageIdDatabase;
import org.thoughtcrime.securesms.database.model.AvatarPickerDatabase;
import org.thoughtcrime.securesms.database.model.databaseprotos.ReactionList;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.jobs.RefreshPreKeysJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.phonenumbers.PhoneNumberFormatter;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.profiles.ProfileName;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.storage.StorageSyncHelper;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.CursorUtil;
import org.thoughtcrime.securesms.util.FileUtils;
import org.thoughtcrime.securesms.util.Hex;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.SqlUtil;
import org.thoughtcrime.securesms.util.Stopwatch;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Triple;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.api.push.DistributionId;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SQLCipherOpenHelper extends SQLiteOpenHelper implements SignalDatabase {

  @SuppressWarnings("unused")
  private static final String TAG = Log.tag(SQLCipherOpenHelper.class);

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
  private static final int REVEALABLE_MESSAGES              = 22;
  private static final int VIEW_ONCE_ONLY                   = 23;
  private static final int RECIPIENT_IDS                    = 24;
  private static final int RECIPIENT_SEARCH                 = 25;
  private static final int RECIPIENT_CLEANUP                = 26;
  private static final int MMS_RECIPIENT_CLEANUP            = 27;
  private static final int ATTACHMENT_HASHING               = 28;
  private static final int NOTIFICATION_RECIPIENT_IDS       = 29;
  private static final int BLUR_HASH                        = 30;
  private static final int MMS_RECIPIENT_CLEANUP_2          = 31;
  private static final int ATTACHMENT_TRANSFORM_PROPERTIES  = 32;
  private static final int ATTACHMENT_CLEAR_HASHES          = 33;
  private static final int ATTACHMENT_CLEAR_HASHES_2        = 34;
  private static final int UUIDS                            = 35;
  private static final int USERNAMES                        = 36;
  private static final int REACTIONS                        = 37;
  private static final int STORAGE_SERVICE                  = 38;
  private static final int REACTIONS_UNREAD_INDEX           = 39;
  private static final int RESUMABLE_DOWNLOADS              = 40;
  private static final int KEY_VALUE_STORE                  = 41;
  private static final int ATTACHMENT_DISPLAY_ORDER         = 42;
  private static final int SPLIT_PROFILE_NAMES              = 43;
  private static final int STICKER_PACK_ORDER               = 44;
  private static final int MEGAPHONES                       = 45;
  private static final int MEGAPHONE_FIRST_APPEARANCE       = 46;
  private static final int PROFILE_KEY_TO_DB                = 47;
  private static final int PROFILE_KEY_CREDENTIALS          = 48;
  private static final int ATTACHMENT_FILE_INDEX            = 49;
  private static final int STORAGE_SERVICE_ACTIVE           = 50;
  private static final int GROUPS_V2_RECIPIENT_CAPABILITY   = 51;
  private static final int TRANSFER_FILE_CLEANUP            = 52;
  private static final int PROFILE_DATA_MIGRATION           = 53;
  private static final int AVATAR_LOCATION_MIGRATION        = 54;
  private static final int GROUPS_V2                        = 55;
  private static final int ATTACHMENT_UPLOAD_TIMESTAMP      = 56;
  private static final int ATTACHMENT_CDN_NUMBER            = 57;
  private static final int JOB_INPUT_DATA                   = 58;
  private static final int SERVER_TIMESTAMP                 = 59;
  private static final int REMOTE_DELETE                    = 60;
  private static final int COLOR_MIGRATION                  = 61;
  private static final int LAST_SCROLLED                    = 62;
  private static final int LAST_PROFILE_FETCH               = 63;
  private static final int SERVER_DELIVERED_TIMESTAMP       = 64;
  private static final int QUOTE_CLEANUP                    = 65;
  private static final int BORDERLESS                       = 66;
  private static final int REMAPPED_RECORDS                 = 67;
  private static final int MENTIONS                         = 68;
  private static final int PINNED_CONVERSATIONS             = 69;
  private static final int MENTION_GLOBAL_SETTING_MIGRATION = 70;
  private static final int UNKNOWN_STORAGE_FIELDS           = 71;
  private static final int STICKER_CONTENT_TYPE             = 72;
  private static final int STICKER_EMOJI_IN_NOTIFICATIONS   = 73;
  private static final int THUMBNAIL_CLEANUP                = 74;
  private static final int STICKER_CONTENT_TYPE_CLEANUP     = 75;
  private static final int MENTION_CLEANUP                  = 76;
  private static final int MENTION_CLEANUP_V2               = 77;
  private static final int REACTION_CLEANUP                 = 78;
  private static final int CAPABILITIES_REFACTOR            = 79;
  private static final int GV1_MIGRATION                    = 80;
  private static final int NOTIFIED_TIMESTAMP               = 81;
  private static final int GV1_MIGRATION_LAST_SEEN          = 82;
  private static final int VIEWED_RECEIPTS                  = 83;
  private static final int CLEAN_UP_GV1_IDS                 = 84;
  private static final int GV1_MIGRATION_REFACTOR           = 85;
  private static final int CLEAR_PROFILE_KEY_CREDENTIALS    = 86;
  private static final int LAST_RESET_SESSION_TIME          = 87;
  private static final int WALLPAPER                        = 88;
  private static final int ABOUT                            = 89;
  private static final int SPLIT_SYSTEM_NAMES               = 90;
  private static final int PAYMENTS                         = 91;
  private static final int CLEAN_STORAGE_IDS                = 92;
  private static final int MP4_GIF_SUPPORT                  = 93;
  private static final int BLUR_AVATARS                     = 94;
  private static final int CLEAN_STORAGE_IDS_WITHOUT_INFO   = 95;
  private static final int CLEAN_REACTION_NOTIFICATIONS     = 96;
  private static final int STORAGE_SERVICE_REFACTOR         = 97;
  private static final int CLEAR_MMS_STORAGE_IDS            = 98;
  private static final int SERVER_GUID                      = 99;
  private static final int CHAT_COLORS                      = 100;
  private static final int AVATAR_COLORS                    = 101;
  private static final int EMOJI_SEARCH                     = 102;
  private static final int SENDER_KEY                       = 103;
  private static final int MESSAGE_DUPE_INDEX               = 104;
  private static final int MESSAGE_LOG                      = 105;
  private static final int MESSAGE_LOG_2                    = 106;
  private static final int ABANDONED_MESSAGE_CLEANUP        = 107;
  private static final int THREAD_AUTOINCREMENT             = 108;
  private static final int MMS_AUTOINCREMENT                = 109;
  private static final int ABANDONED_ATTACHMENT_CLEANUP     = 110;
  private static final int AVATAR_PICKER                    = 111;
  private static final int THREAD_CLEANUP                   = 112;

  private static final int    DATABASE_VERSION = 112;
  private static final String DATABASE_NAME    = "signal.db";

  private final Context        context;
  private final DatabaseSecret databaseSecret;

  public SQLCipherOpenHelper(@NonNull Context context, @NonNull DatabaseSecret databaseSecret) {
    super(context, DATABASE_NAME, null, DATABASE_VERSION, new SqlCipherDatabaseHook(), new SqlCipherErrorHandler(DATABASE_NAME));

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
    db.execSQL(SenderKeyDatabase.CREATE_TABLE);
    db.execSQL(SenderKeySharedDatabase.CREATE_TABLE);
    db.execSQL(PendingRetryReceiptDatabase.CREATE_TABLE);
    db.execSQL(StickerDatabase.CREATE_TABLE);
    db.execSQL(UnknownStorageIdDatabase.CREATE_TABLE);
    db.execSQL(MentionDatabase.CREATE_TABLE);
    db.execSQL(PaymentDatabase.CREATE_TABLE);
    db.execSQL(ChatColorsDatabase.CREATE_TABLE);
    db.execSQL(EmojiSearchDatabase.CREATE_TABLE);
    db.execSQL(AvatarPickerDatabase.CREATE_TABLE);
    executeStatements(db, SearchDatabase.CREATE_TABLE);
    executeStatements(db, RemappedRecordsDatabase.CREATE_TABLE);
    executeStatements(db, MessageSendLogDatabase.CREATE_TABLE);

    executeStatements(db, RecipientDatabase.CREATE_INDEXS);
    executeStatements(db, SmsDatabase.CREATE_INDEXS);
    executeStatements(db, MmsDatabase.CREATE_INDEXS);
    executeStatements(db, AttachmentDatabase.CREATE_INDEXS);
    executeStatements(db, ThreadDatabase.CREATE_INDEXS);
    executeStatements(db, DraftDatabase.CREATE_INDEXS);
    executeStatements(db, GroupDatabase.CREATE_INDEXS);
    executeStatements(db, GroupReceiptDatabase.CREATE_INDEXES);
    executeStatements(db, StickerDatabase.CREATE_INDEXES);
    executeStatements(db, UnknownStorageIdDatabase.CREATE_INDEXES);
    executeStatements(db, MentionDatabase.CREATE_INDEXES);
    executeStatements(db, PaymentDatabase.CREATE_INDEXES);
    executeStatements(db, MessageSendLogDatabase.CREATE_INDEXES);

    executeStatements(db, MessageSendLogDatabase.CREATE_TRIGGERS);

    if (context.getDatabasePath(ClassicOpenHelper.NAME).exists()) {
      ClassicOpenHelper                      legacyHelper = new ClassicOpenHelper(context);
      android.database.sqlite.SQLiteDatabase legacyDb     = legacyHelper.getWritableDatabase();

      SQLCipherMigrationHelper.migratePlaintext(context, legacyDb, db);

      MasterSecret masterSecret = KeyCachingService.getMasterSecret(context);

      if (masterSecret != null) SQLCipherMigrationHelper.migrateCiphertext(context, masterSecret, legacyDb, db, null);
      else                      TextSecurePreferences.setNeedsSqlCipherMigration(context, true);

      if (!PreKeyMigrationHelper.migratePreKeys(context, db)) {
        ApplicationDependencies.getJobManager().add(new RefreshPreKeysJob());
      }

      SessionStoreMigrationHelper.migrateSessions(context, db);
      PreKeyMigrationHelper.cleanUpPreKeys(context);
    }
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    Log.i(TAG, "Upgrading database: " + oldVersion + ", " + newVersion);
    long startTime = System.currentTimeMillis();

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
          ApplicationDependencies.getJobManager().add(new RefreshPreKeysJob());
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
      if (oldVersion < QUOTE_MISSING && !SqlUtil.columnExists(db, "mms", "quote_missing")) {
        db.execSQL("ALTER TABLE mms ADD COLUMN quote_missing INTEGER DEFAULT 0");
      }

      // Note: The column only being checked due to upgrade issues as described in #8184
      if (oldVersion < NOTIFICATION_CHANNELS && !SqlUtil.columnExists(db, "recipient_preferences", "notification_channel")) {
        db.execSQL("ALTER TABLE recipient_preferences ADD COLUMN notification_channel TEXT DEFAULT NULL");
        NotificationChannels.create(context);

        try (Cursor cursor = db.rawQuery("SELECT recipient_ids, system_display_name, signal_profile_name, notification, vibrate FROM recipient_preferences WHERE notification NOT NULL OR vibrate != 0", null)) {
          while (cursor != null && cursor.moveToNext()) {
            String  rawAddress      = cursor.getString(cursor.getColumnIndexOrThrow("recipient_ids"));
            String  address         = PhoneNumberFormatter.get(context).format(rawAddress);
            String  systemName      = cursor.getString(cursor.getColumnIndexOrThrow("system_display_name"));
            String  profileName     = cursor.getString(cursor.getColumnIndexOrThrow("signal_profile_name"));
            String  messageSound    = cursor.getString(cursor.getColumnIndexOrThrow("notification"));
            Uri     messageSoundUri = messageSound != null ? Uri.parse(messageSound) : null;
            int     vibrateState    = cursor.getInt(cursor.getColumnIndexOrThrow("vibrate"));
            String  displayName     = NotificationChannels.getChannelDisplayNameFor(context, systemName, profileName, null, address);
            boolean vibrateEnabled  = vibrateState == 0 ? SignalStore.settings().isMessageVibrateEnabled() :vibrateState == 1;

            if (GroupId.isEncodedGroup(address)) {
              try(Cursor groupCursor = db.rawQuery("SELECT title FROM groups WHERE group_id = ?", new String[] { address })) {
                if (groupCursor != null && groupCursor.moveToFirst()) {
                  String title = groupCursor.getString(groupCursor.getColumnIndexOrThrow("title"));

                  if (!TextUtils.isEmpty(title)) {
                    displayName = title;
                  }
                }
              }
            }

            String channelId = NotificationChannels.createChannelFor(context, "contact_" + address + "_" + System.currentTimeMillis(), displayName, messageSoundUri, vibrateEnabled, null);

            ContentValues values = new ContentValues(1);
            values.put("notification_channel", channelId);
            db.update("recipient_preferences", values, "recipient_ids = ?", new String[] { rawAddress });
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
        if (!SqlUtil.columnExists(db, "part", "caption")) {
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

      if (oldVersion < REVEALABLE_MESSAGES) {
        db.execSQL("ALTER TABLE mms ADD COLUMN reveal_duration INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE mms ADD COLUMN reveal_start_time INTEGER DEFAULT 0");

        db.execSQL("ALTER TABLE thread ADD COLUMN snippet_content_type TEXT DEFAULT NULL");
        db.execSQL("ALTER TABLE thread ADD COLUMN snippet_extras TEXT DEFAULT NULL");
      }

      if (oldVersion < VIEW_ONCE_ONLY) {
        db.execSQL("UPDATE mms SET reveal_duration = 1 WHERE reveal_duration > 0");
        db.execSQL("UPDATE mms SET reveal_start_time = 0");
      }

      if (oldVersion < RECIPIENT_IDS) {
        RecipientIdMigrationHelper.execute(db);
      }

      if (oldVersion < RECIPIENT_SEARCH) {
        db.execSQL("ALTER TABLE recipient ADD COLUMN system_phone_type INTEGER DEFAULT -1");

        String localNumber = TextSecurePreferences.getLocalNumber(context);
        if (!TextUtils.isEmpty(localNumber)) {
          try (Cursor cursor = db.query("recipient", null, "phone = ?", new String[] { localNumber }, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) {
              ContentValues values = new ContentValues();
              values.put("phone", localNumber);
              values.put("registered", 1);
              values.put("profile_sharing", 1);
              db.insert("recipient", null, values);
            } else {
              db.execSQL("UPDATE recipient SET registered = ?, profile_sharing = ? WHERE phone = ?",
                         new String[] { "1", "1", localNumber });
            }
          }
        }
      }

      if (oldVersion < RECIPIENT_CLEANUP) {
        RecipientIdCleanupHelper.execute(db);
      }

      if (oldVersion < MMS_RECIPIENT_CLEANUP) {
        ContentValues values = new ContentValues(1);
        values.put("address", "-1");
        int count = db.update("mms", values, "address = ?", new String[] { "0" });
        Log.i(TAG, "MMS recipient cleanup updated " + count + " rows.");
      }

      if (oldVersion < ATTACHMENT_HASHING) {
        db.execSQL("ALTER TABLE part ADD COLUMN data_hash TEXT DEFAULT NULL");
        db.execSQL("CREATE INDEX IF NOT EXISTS part_data_hash_index ON part (data_hash)");
      }

      if (oldVersion < NOTIFICATION_RECIPIENT_IDS && Build.VERSION.SDK_INT >= 26) {
        NotificationManager       notificationManager = ServiceUtil.getNotificationManager(context);
        List<NotificationChannel> channels            = Stream.of(notificationManager.getNotificationChannels())
                                                              .filter(c -> c.getId().startsWith("contact_"))
                                                              .toList();

        Log.i(TAG, "Migrating " + channels.size() + " channels to use RecipientId's.");

        for (NotificationChannel oldChannel : channels) {
          notificationManager.deleteNotificationChannel(oldChannel.getId());

          int    startIndex = "contact_".length();
          int    endIndex   = oldChannel.getId().lastIndexOf("_");
          String address    = oldChannel.getId().substring(startIndex, endIndex);

          String recipientId;

          try (Cursor cursor = db.query("recipient", new String[] { "_id" }, "phone = ? OR email = ? OR group_id = ?", new String[] { address, address, address}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
              recipientId = cursor.getString(cursor.getColumnIndexOrThrow("_id"));
            } else {
              Log.w(TAG, "Couldn't find recipient for address: " + address);
              continue;
            }
          }

          String              newId      = "contact_" + recipientId + "_" + System.currentTimeMillis();
          NotificationChannel newChannel = new NotificationChannel(newId, oldChannel.getName(), oldChannel.getImportance());

          Log.i(TAG, "Updating channel ID from '" + oldChannel.getId() + "' to '" + newChannel.getId() + "'.");

          newChannel.setGroup(oldChannel.getGroup());
          newChannel.setSound(oldChannel.getSound(), oldChannel.getAudioAttributes());
          newChannel.setBypassDnd(oldChannel.canBypassDnd());
          newChannel.enableVibration(oldChannel.shouldVibrate());
          newChannel.setVibrationPattern(oldChannel.getVibrationPattern());
          newChannel.setLockscreenVisibility(oldChannel.getLockscreenVisibility());
          newChannel.setShowBadge(oldChannel.canShowBadge());
          newChannel.setLightColor(oldChannel.getLightColor());
          newChannel.enableLights(oldChannel.shouldShowLights());

          notificationManager.createNotificationChannel(newChannel);

          ContentValues contentValues = new ContentValues(1);
          contentValues.put("notification_channel", newChannel.getId());
          db.update("recipient", contentValues, "_id = ?", new String[] { recipientId });
        }
      }

      if (oldVersion < BLUR_HASH) {
        db.execSQL("ALTER TABLE part ADD COLUMN blur_hash TEXT DEFAULT NULL");
      }

      if (oldVersion < MMS_RECIPIENT_CLEANUP_2) {
        ContentValues values = new ContentValues(1);
        values.put("address", "-1");
        int count = db.update("mms", values, "address = ? OR address IS NULL", new String[] { "0" });
        Log.i(TAG, "MMS recipient cleanup 2 updated " + count + " rows.");
      }

      if (oldVersion < ATTACHMENT_TRANSFORM_PROPERTIES) {
        db.execSQL("ALTER TABLE part ADD COLUMN transform_properties TEXT DEFAULT NULL");
      }

      if (oldVersion < ATTACHMENT_CLEAR_HASHES) {
        db.execSQL("UPDATE part SET data_hash = null");
      }

      if (oldVersion < ATTACHMENT_CLEAR_HASHES_2) {
        db.execSQL("UPDATE part SET data_hash = null");
        Glide.get(context).clearDiskCache();
      }

      if (oldVersion < UUIDS) {
        db.execSQL("ALTER TABLE recipient ADD COLUMN uuid_supported INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE push ADD COLUMN source_uuid TEXT DEFAULT NULL");
      }

      if (oldVersion < USERNAMES) {
        db.execSQL("ALTER TABLE recipient ADD COLUMN username TEXT DEFAULT NULL");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS recipient_username_index ON recipient (username)");
      }

      if (oldVersion < REACTIONS) {
        db.execSQL("ALTER TABLE sms ADD COLUMN reactions BLOB DEFAULT NULL");
        db.execSQL("ALTER TABLE mms ADD COLUMN reactions BLOB DEFAULT NULL");

        db.execSQL("ALTER TABLE sms ADD COLUMN reactions_unread INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE mms ADD COLUMN reactions_unread INTEGER DEFAULT 0");

        db.execSQL("ALTER TABLE sms ADD COLUMN reactions_last_seen INTEGER DEFAULT -1");
        db.execSQL("ALTER TABLE mms ADD COLUMN reactions_last_seen INTEGER DEFAULT -1");
      }

      if (oldVersion < STORAGE_SERVICE) {
        db.execSQL("CREATE TABLE storage_key (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                             "type INTEGER, " +
                                             "key TEXT UNIQUE)");
        db.execSQL("CREATE INDEX IF NOT EXISTS storage_key_type_index ON storage_key (type)");

        db.execSQL("ALTER TABLE recipient ADD COLUMN system_info_pending INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE recipient ADD COLUMN storage_service_key TEXT DEFAULT NULL");
        db.execSQL("ALTER TABLE recipient ADD COLUMN dirty INTEGER DEFAULT 0");

        db.execSQL("CREATE UNIQUE INDEX recipient_storage_service_key ON recipient (storage_service_key)");
        db.execSQL("CREATE INDEX recipient_dirty_index ON recipient (dirty)");
      }

      if (oldVersion < REACTIONS_UNREAD_INDEX) {
        db.execSQL("CREATE INDEX IF NOT EXISTS sms_reactions_unread_index ON sms (reactions_unread);");
        db.execSQL("CREATE INDEX IF NOT EXISTS mms_reactions_unread_index ON mms (reactions_unread);");
      }

      if (oldVersion < RESUMABLE_DOWNLOADS) {
        db.execSQL("ALTER TABLE part ADD COLUMN transfer_file TEXT DEFAULT NULL");
      }

      if (oldVersion < KEY_VALUE_STORE) {
        db.execSQL("CREATE TABLE key_value (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                           "key TEXT UNIQUE, " +
                                           "value TEXT, " +
                                           "type INTEGER)");
      }

      if (oldVersion < ATTACHMENT_DISPLAY_ORDER) {
        db.execSQL("ALTER TABLE part ADD COLUMN display_order INTEGER DEFAULT 0");
      }

      if (oldVersion < SPLIT_PROFILE_NAMES) {
        db.execSQL("ALTER TABLE recipient ADD COLUMN profile_family_name TEXT DEFAULT NULL");
        db.execSQL("ALTER TABLE recipient ADD COLUMN profile_joined_name TEXT DEFAULT NULL");
      }

      if (oldVersion < STICKER_PACK_ORDER) {
        db.execSQL("ALTER TABLE sticker ADD COLUMN pack_order INTEGER DEFAULT 0");
      }

      if (oldVersion < MEGAPHONES) {
        db.execSQL("CREATE TABLE megaphone (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                           "event TEXT UNIQUE, "  +
                                           "seen_count INTEGER, " +
                                           "last_seen INTEGER, "  +
                                           "finished INTEGER)");
      }

      if (oldVersion < MEGAPHONE_FIRST_APPEARANCE) {
        db.execSQL("ALTER TABLE megaphone ADD COLUMN first_visible INTEGER DEFAULT 0");
      }

      if (oldVersion < PROFILE_KEY_TO_DB) {
        String localNumber = TextSecurePreferences.getLocalNumber(context);
        if (!TextUtils.isEmpty(localNumber)) {
          String        encodedProfileKey = PreferenceManager.getDefaultSharedPreferences(context).getString("pref_profile_key", null);
          byte[]        profileKey        = encodedProfileKey != null ? Base64.decodeOrThrow(encodedProfileKey) : Util.getSecretBytes(32);
          ContentValues values            = new ContentValues(1);

          values.put("profile_key", Base64.encodeBytes(profileKey));

          if (db.update("recipient", values, "phone = ?", new String[]{localNumber}) == 0) {
            throw new AssertionError("No rows updated!");
          }
        }
      }

      if (oldVersion < PROFILE_KEY_CREDENTIALS) {
        db.execSQL("ALTER TABLE recipient ADD COLUMN profile_key_credential TEXT DEFAULT NULL");
      }

      if (oldVersion < ATTACHMENT_FILE_INDEX) {
        db.execSQL("CREATE INDEX IF NOT EXISTS part_data_index ON part (_data)");
      }

      if (oldVersion < STORAGE_SERVICE_ACTIVE) {
        db.execSQL("ALTER TABLE recipient ADD COLUMN group_type INTEGER DEFAULT 0");
        db.execSQL("CREATE INDEX IF NOT EXISTS recipient_group_type_index ON recipient (group_type)");

        db.execSQL("UPDATE recipient set group_type = 1 WHERE group_id NOT NULL AND group_id LIKE '__signal_mms_group__%'");
        db.execSQL("UPDATE recipient set group_type = 2 WHERE group_id NOT NULL AND group_id LIKE '__textsecure_group__%'");

        try (Cursor cursor = db.rawQuery("SELECT _id FROM recipient WHERE registered = 1 or group_type = 2", null)) {
          while (cursor != null && cursor.moveToNext()) {
            String        id     = cursor.getString(cursor.getColumnIndexOrThrow("_id"));
            ContentValues values = new ContentValues(1);

            values.put("dirty", 2);
            values.put("storage_service_key", Base64.encodeBytes(StorageSyncHelper.generateKey()));

            db.update("recipient", values, "_id = ?", new String[] { id });
          }
        }
      }

      if (oldVersion < GROUPS_V2_RECIPIENT_CAPABILITY) {
        db.execSQL("ALTER TABLE recipient ADD COLUMN gv2_capability INTEGER DEFAULT 0");
      }

      if (oldVersion < TRANSFER_FILE_CLEANUP) {
        File partsDirectory = context.getDir("parts", Context.MODE_PRIVATE);

        if (partsDirectory.exists()) {
          File[] transferFiles = partsDirectory.listFiles((dir, name) -> name.startsWith("transfer"));
          int    deleteCount   = 0;

          Log.i(TAG, "Found " + transferFiles.length + " dangling transfer files.");

          for (File file : transferFiles) {
            if (file.delete()) {
              Log.i(TAG, "Deleted " + file.getName());
              deleteCount++;
            }
          }

          Log.i(TAG, "Deleted " + deleteCount + " dangling transfer files.");
        } else {
          Log.w(TAG, "Part directory did not exist. Skipping.");
        }
      }

      if (oldVersion < PROFILE_DATA_MIGRATION) {
        String localNumber = TextSecurePreferences.getLocalNumber(context);
        if (localNumber != null) {
          String      encodedProfileName = PreferenceManager.getDefaultSharedPreferences(context).getString("pref_profile_name", null);
          ProfileName profileName        = ProfileName.fromSerialized(encodedProfileName);

          db.execSQL("UPDATE recipient SET signal_profile_name = ?, profile_family_name = ?, profile_joined_name = ? WHERE phone = ?",
                     new String[] { profileName.getGivenName(), profileName.getFamilyName(), profileName.toString(), localNumber });
        }
      }

      if (oldVersion < AVATAR_LOCATION_MIGRATION) {
        File   oldAvatarDirectory = new File(context.getFilesDir(), "avatars");
        File[] results            = oldAvatarDirectory.listFiles();

        if (results != null) {
          Log.i(TAG, "Preparing to migrate " + results.length + " avatars.");

          for (File file : results) {
            if (Util.isLong(file.getName())) {
              try {
                AvatarHelper.setAvatar(context, RecipientId.from(file.getName()), new FileInputStream(file));
              } catch(IOException e) {
                Log.w(TAG, "Failed to copy file " + file.getName() + "! Skipping.");
              }
            } else {
              Log.w(TAG, "Invalid avatar name '" + file.getName() + "'! Skipping.");
            }
          }
        } else {
          Log.w(TAG, "No avatar directory files found.");
        }

        if (!FileUtils.deleteDirectory(oldAvatarDirectory)) {
          Log.w(TAG, "Failed to delete avatar directory.");
        }

        try (Cursor cursor = db.rawQuery("SELECT recipient_id, avatar FROM groups", null)) {
          while (cursor != null && cursor.moveToNext()) {
            RecipientId recipientId = RecipientId.from(cursor.getLong(cursor.getColumnIndexOrThrow("recipient_id")));
            byte[]      avatar      = cursor.getBlob(cursor.getColumnIndexOrThrow("avatar"));

            try {
              AvatarHelper.setAvatar(context, recipientId, avatar != null ? new ByteArrayInputStream(avatar) : null);
            } catch (IOException e) {
              Log.w(TAG, "Failed to copy avatar for " + recipientId + "! Skipping.", e);
            }
          }
        }

        db.execSQL("UPDATE groups SET avatar_id = 0 WHERE avatar IS NULL");
        db.execSQL("UPDATE groups SET avatar = NULL");
      }

      if (oldVersion < GROUPS_V2) {
        db.execSQL("ALTER TABLE groups ADD COLUMN master_key");
        db.execSQL("ALTER TABLE groups ADD COLUMN revision");
        db.execSQL("ALTER TABLE groups ADD COLUMN decrypted_group");
      }

      if (oldVersion < ATTACHMENT_UPLOAD_TIMESTAMP) {
        db.execSQL("ALTER TABLE part ADD COLUMN upload_timestamp DEFAULT 0");
      }

      if (oldVersion < ATTACHMENT_CDN_NUMBER) {
        db.execSQL("ALTER TABLE part ADD COLUMN cdn_number INTEGER DEFAULT 0");
      }

      if (oldVersion < JOB_INPUT_DATA) {
        db.execSQL("ALTER TABLE job_spec ADD COLUMN serialized_input_data TEXT DEFAULT NULL");
      }

      if (oldVersion < SERVER_TIMESTAMP) {
        db.execSQL("ALTER TABLE sms ADD COLUMN date_server INTEGER DEFAULT -1");
        db.execSQL("CREATE INDEX IF NOT EXISTS sms_date_server_index ON sms (date_server)");

        db.execSQL("ALTER TABLE mms ADD COLUMN date_server INTEGER DEFAULT -1");
        db.execSQL("CREATE INDEX IF NOT EXISTS mms_date_server_index ON mms (date_server)");
      }

      if (oldVersion < REMOTE_DELETE) {
        db.execSQL("ALTER TABLE sms ADD COLUMN remote_deleted INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE mms ADD COLUMN remote_deleted INTEGER DEFAULT 0");
      }

      if (oldVersion < COLOR_MIGRATION) {
        try (Cursor cursor = db.rawQuery("SELECT _id, system_display_name FROM recipient WHERE system_display_name NOT NULL AND color IS NULL", null)) {
          while (cursor != null && cursor.moveToNext()) {
            long   id   = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
            String name = cursor.getString(cursor.getColumnIndexOrThrow("system_display_name"));

            ContentValues values = new ContentValues();
            values.put("color", ContactColorsLegacy.generateForV2(name).serialize());

            db.update("recipient", values, "_id = ?", new String[] { String.valueOf(id) });
          }
        }
      }

      if (oldVersion < LAST_SCROLLED) {
        db.execSQL("ALTER TABLE thread ADD COLUMN last_scrolled INTEGER DEFAULT 0");
      }

      if (oldVersion < LAST_PROFILE_FETCH) {
        db.execSQL("ALTER TABLE recipient ADD COLUMN last_profile_fetch INTEGER DEFAULT 0");
      }

      if (oldVersion < SERVER_DELIVERED_TIMESTAMP) {
        db.execSQL("ALTER TABLE push ADD COLUMN server_delivered_timestamp INTEGER DEFAULT 0");
      }

      if (oldVersion < QUOTE_CLEANUP) {
        String query = "SELECT _data " +
                       "FROM (SELECT _data, MIN(quote) AS all_quotes " +
                             "FROM part " +
                             "WHERE _data NOT NULL AND data_hash NOT NULL " +
                             "GROUP BY _data) " +
                       "WHERE all_quotes = 1";

        int count = 0;

        try (Cursor cursor = db.rawQuery(query, null)) {
          while (cursor != null && cursor.moveToNext()) {
            String data = cursor.getString(cursor.getColumnIndexOrThrow("_data"));

            if (new File(data).delete()) {
              ContentValues values = new ContentValues();
              values.putNull("_data");
              values.putNull("data_random");
              values.putNull("thumbnail");
              values.putNull("thumbnail_random");
              values.putNull("data_hash");
              db.update("part", values, "_data = ?", new String[] { data });

              count++;
            } else {
              Log.w(TAG, "[QuoteCleanup] Failed to delete " + data);
            }
          }
        }

        Log.i(TAG, "[QuoteCleanup] Cleaned up " + count + " quotes.");
      }

      if (oldVersion < BORDERLESS) {
        db.execSQL("ALTER TABLE part ADD COLUMN borderless INTEGER DEFAULT 0");
      }

      if (oldVersion < REMAPPED_RECORDS) {
        db.execSQL("CREATE TABLE remapped_recipients (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                                     "old_id INTEGER UNIQUE, " +
                                                     "new_id INTEGER)");
        db.execSQL("CREATE TABLE remapped_threads (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                                  "old_id INTEGER UNIQUE, " +
                                                  "new_id INTEGER)");
      }

      if (oldVersion < MENTIONS) {
        db.execSQL("CREATE TABLE mention (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                         "thread_id INTEGER, " +
                                         "message_id INTEGER, " +
                                         "recipient_id INTEGER, " +
                                         "range_start INTEGER, " +
                                         "range_length INTEGER)");

        db.execSQL("CREATE INDEX IF NOT EXISTS mention_message_id_index ON mention (message_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS mention_recipient_id_thread_id_index ON mention (recipient_id, thread_id);");

        db.execSQL("ALTER TABLE mms ADD COLUMN quote_mentions BLOB DEFAULT NULL");
        db.execSQL("ALTER TABLE mms ADD COLUMN mentions_self INTEGER DEFAULT 0");

        db.execSQL("ALTER TABLE recipient ADD COLUMN mention_setting INTEGER DEFAULT 0");
      }

      if (oldVersion < PINNED_CONVERSATIONS) {
        db.execSQL("ALTER TABLE thread ADD COLUMN pinned INTEGER DEFAULT 0");
        db.execSQL("CREATE INDEX IF NOT EXISTS thread_pinned_index ON thread (pinned)");
      }

      if (oldVersion < MENTION_GLOBAL_SETTING_MIGRATION) {
        ContentValues updateAlways = new ContentValues();
        updateAlways.put("mention_setting", 0);
        db.update("recipient", updateAlways, "mention_setting = 1", null);

        ContentValues updateNever = new ContentValues();
        updateNever.put("mention_setting", 1);
        db.update("recipient", updateNever, "mention_setting = 2", null);
      }

      if (oldVersion < UNKNOWN_STORAGE_FIELDS) {
        db.execSQL("ALTER TABLE recipient ADD COLUMN storage_proto TEXT DEFAULT NULL");
      }

      if (oldVersion < STICKER_CONTENT_TYPE) {
        db.execSQL("ALTER TABLE sticker ADD COLUMN content_type TEXT DEFAULT NULL");
      }

      if (oldVersion < STICKER_EMOJI_IN_NOTIFICATIONS) {
        db.execSQL("ALTER TABLE part ADD COLUMN sticker_emoji TEXT DEFAULT NULL");
      }

      if (oldVersion < THUMBNAIL_CLEANUP) {
        int total   = 0;
        int deleted = 0;

        try (Cursor cursor = db.rawQuery("SELECT thumbnail FROM part WHERE thumbnail NOT NULL", null)) {
          if (cursor != null) {
            total = cursor.getCount();
            Log.w(TAG, "Found " + total + " thumbnails to delete.");
          }

          while (cursor != null && cursor.moveToNext()) {
            File file = new File(CursorUtil.requireString(cursor, "thumbnail"));

            if (file.delete()) {
              deleted++;
            } else {
              Log.w(TAG, "Failed to delete file! " + file.getAbsolutePath());
            }
          }
        }

        Log.w(TAG, "Deleted " + deleted + "/" + total + " thumbnail files.");
      }

      if (oldVersion < STICKER_CONTENT_TYPE_CLEANUP) {
        ContentValues values = new ContentValues();
        values.put("ct", "image/webp");

        String query = "sticker_id NOT NULL AND (ct IS NULL OR ct = '')";

        int rows = db.update("part", values, query, null);
        Log.i(TAG, "Updated " + rows + " sticker attachment content types.");
      }

      if (oldVersion < MENTION_CLEANUP) {
        String selectMentionIdsNotInGroupsV2 = "select mention._id from mention left join thread on mention.thread_id = thread._id left join recipient on thread.recipient_ids = recipient._id where recipient.group_type != 3";
        db.delete("mention", "_id in (" + selectMentionIdsNotInGroupsV2 + ")", null);
        db.delete("mention", "message_id NOT IN (SELECT _id FROM mms) OR thread_id NOT IN (SELECT _id from thread)", null);

        List<Long> idsToDelete = new LinkedList<>();
        try (Cursor cursor = db.rawQuery("select mention.*, mms.body from mention inner join mms on mention.message_id = mms._id", null)) {
          while (cursor != null && cursor.moveToNext()) {
            int    rangeStart  = CursorUtil.requireInt(cursor, "range_start");
            int    rangeLength = CursorUtil.requireInt(cursor, "range_length");
            String body        = CursorUtil.requireString(cursor, "body");

            if (body == null || body.isEmpty() || rangeStart < 0 || rangeLength < 0 || (rangeStart + rangeLength) > body.length()) {
              idsToDelete.add(CursorUtil.requireLong(cursor, "_id"));
            }
          }
        }

        if (Util.hasItems(idsToDelete)) {
          String ids = TextUtils.join(",", idsToDelete);
          db.delete("mention", "_id in (" + ids + ")", null);
        }
      }

      if (oldVersion < MENTION_CLEANUP_V2) {
        String selectMentionIdsWithMismatchingThreadIds = "select mention._id from mention left join mms on mention.message_id = mms._id where mention.thread_id != mms.thread_id";
        db.delete("mention", "_id in (" + selectMentionIdsWithMismatchingThreadIds + ")", null);

        List<Long>                          idsToDelete   = new LinkedList<>();
        Set<Triple<Long, Integer, Integer>> mentionTuples = new HashSet<>();
        try (Cursor cursor = db.rawQuery("select mention.*, mms.body from mention inner join mms on mention.message_id = mms._id order by mention._id desc", null)) {
          while (cursor != null && cursor.moveToNext()) {
            long   mentionId   = CursorUtil.requireLong(cursor, "_id");
            long   messageId   = CursorUtil.requireLong(cursor, "message_id");
            int    rangeStart  = CursorUtil.requireInt(cursor, "range_start");
            int    rangeLength = CursorUtil.requireInt(cursor, "range_length");
            String body        = CursorUtil.requireString(cursor, "body");

            if (body != null && rangeStart < body.length() && body.charAt(rangeStart) != '\uFFFC') {
              idsToDelete.add(mentionId);
            } else {
              Triple<Long, Integer, Integer> tuple = new Triple<>(messageId, rangeStart, rangeLength);
              if (mentionTuples.contains(tuple)) {
                idsToDelete.add(mentionId);
              } else {
                mentionTuples.add(tuple);
              }
            }
          }

          if (Util.hasItems(idsToDelete)) {
            String ids = TextUtils.join(",", idsToDelete);
            db.delete("mention", "_id in (" + ids + ")", null);
          }
        }
      }

      if (oldVersion < REACTION_CLEANUP) {
        ContentValues values = new ContentValues();
        values.putNull("reactions");
        db.update("sms", values, "remote_deleted = ?", new String[] { "1" });
      }

      if (oldVersion < CAPABILITIES_REFACTOR) {
        db.execSQL("ALTER TABLE recipient ADD COLUMN capabilities INTEGER DEFAULT 0");

        db.execSQL("UPDATE recipient SET capabilities = 1 WHERE gv2_capability = 1");
        db.execSQL("UPDATE recipient SET capabilities = 2 WHERE gv2_capability = -1");
      }

      if (oldVersion < GV1_MIGRATION) {
        db.execSQL("ALTER TABLE groups ADD COLUMN expected_v2_id TEXT DEFAULT NULL");
        db.execSQL("ALTER TABLE groups ADD COLUMN former_v1_members TEXT DEFAULT NULL");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS expected_v2_id_index ON groups (expected_v2_id)");

        int count = 0;
        try (Cursor cursor = db.rawQuery("SELECT * FROM groups WHERE group_id LIKE '__textsecure_group__!%' AND LENGTH(group_id) = 53", null)) {
          while (cursor.moveToNext()) {
            String gv1 = CursorUtil.requireString(cursor, "group_id");
            String gv2 = GroupId.parseOrThrow(gv1).requireV1().deriveV2MigrationGroupId().toString();

            ContentValues values = new ContentValues();
            values.put("expected_v2_id", gv2);
            count += db.update("groups", values, "group_id = ?", SqlUtil.buildArgs(gv1));
          }
        }

        Log.i(TAG, "Updated " + count + " GV1 groups with expected GV2 IDs.");
      }

      if (oldVersion < NOTIFIED_TIMESTAMP) {
        db.execSQL("ALTER TABLE sms ADD COLUMN notified_timestamp INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE mms ADD COLUMN notified_timestamp INTEGER DEFAULT 0");
      }

      if (oldVersion < GV1_MIGRATION_LAST_SEEN) {
        db.execSQL("ALTER TABLE recipient ADD COLUMN last_gv1_migrate_reminder INTEGER DEFAULT 0");
      }

      if (oldVersion < VIEWED_RECEIPTS) {
        db.execSQL("ALTER TABLE mms ADD COLUMN viewed_receipt_count INTEGER DEFAULT 0");
      }

      if (oldVersion < CLEAN_UP_GV1_IDS) {
        List<String> deletableRecipients = new LinkedList<>();
        try (Cursor cursor = db.rawQuery("SELECT _id, group_id FROM recipient\n" +
                                         "WHERE group_id NOT IN (SELECT group_id FROM groups)\n" +
                                         "AND group_id LIKE '__textsecure_group__!%' AND length(group_id) <> 53\n" +
                                         "AND (_id NOT IN (SELECT recipient_ids FROM thread) OR _id IN (SELECT recipient_ids FROM thread WHERE message_count = 0))", null))
        {
          while (cursor.moveToNext()) {
            String recipientId = cursor.getString(cursor.getColumnIndexOrThrow("_id"));
            String groupIdV1   = cursor.getString(cursor.getColumnIndexOrThrow("group_id"));
            deletableRecipients.add(recipientId);
            Log.d(TAG, String.format(Locale.US, "Found invalid GV1 on %s with no or empty thread %s length %d", recipientId, groupIdV1, groupIdV1.length()));
          }
        }

        for (String recipientId : deletableRecipients) {
          db.delete("recipient", "_id = ?", new String[]{recipientId});
          Log.d(TAG, "Deleted recipient " + recipientId);
        }

        List<String> orphanedThreads = new LinkedList<>();
        try (Cursor cursor = db.rawQuery("SELECT _id FROM thread WHERE message_count = 0 AND recipient_ids NOT IN (SELECT _id FROM recipient)", null)) {
          while (cursor.moveToNext()) {
            orphanedThreads.add(cursor.getString(cursor.getColumnIndexOrThrow("_id")));
          }
        }

        for (String orphanedThreadId : orphanedThreads) {
          db.delete("thread", "_id = ?", new String[]{orphanedThreadId});
          Log.d(TAG, "Deleted orphaned thread " + orphanedThreadId);
        }

        List<String> remainingInvalidGV1Recipients = new LinkedList<>();
        try (Cursor cursor = db.rawQuery("SELECT _id, group_id FROM recipient\n" +
                                         "WHERE group_id NOT IN (SELECT group_id FROM groups)\n" +
                                         "AND group_id LIKE '__textsecure_group__!%' AND length(group_id) <> 53\n" +
                                         "AND _id IN (SELECT recipient_ids FROM thread)", null))
        {
          while (cursor.moveToNext()) {
            String recipientId = cursor.getString(cursor.getColumnIndexOrThrow("_id"));
            String groupIdV1   = cursor.getString(cursor.getColumnIndexOrThrow("group_id"));
            remainingInvalidGV1Recipients.add(recipientId);
            Log.d(TAG, String.format(Locale.US, "Found invalid GV1 on %s with non-empty thread %s length %d", recipientId, groupIdV1, groupIdV1.length()));
          }
        }

        for (String recipientId : remainingInvalidGV1Recipients) {
          String        newId  = "__textsecure_group__!" + Hex.toStringCondensed(Util.getSecretBytes(16));
          ContentValues values = new ContentValues(1);
          values.put("group_id", newId);

          db.update("recipient", values, "_id = ?", new String[] { String.valueOf(recipientId) });
          Log.d(TAG, String.format("Replaced group id on recipient %s now %s", recipientId, newId));
        }
      }

      if (oldVersion < GV1_MIGRATION_REFACTOR) {
        ContentValues values = new ContentValues(1);
        values.putNull("former_v1_members");

        int count = db.update("groups", values, "former_v1_members NOT NULL", null);

        Log.i(TAG, "Cleared former_v1_members for " + count + " rows");
      }

      if (oldVersion < CLEAR_PROFILE_KEY_CREDENTIALS) {
        ContentValues values = new ContentValues(1);
        values.putNull("profile_key_credential");

        int count = db.update("recipient", values, "profile_key_credential NOT NULL", null);

        Log.i(TAG, "Cleared profile key credentials for " + count + " rows");
      }

      if (oldVersion < LAST_RESET_SESSION_TIME) {
        db.execSQL("ALTER TABLE recipient ADD COLUMN last_session_reset BLOB DEFAULT NULL");
      }

      if (oldVersion < WALLPAPER) {
        db.execSQL("ALTER TABLE recipient ADD COLUMN wallpaper BLOB DEFAULT NULL");
        db.execSQL("ALTER TABLE recipient ADD COLUMN wallpaper_file TEXT DEFAULT NULL");
      }

      if (oldVersion < ABOUT) {
        db.execSQL("ALTER TABLE recipient ADD COLUMN about TEXT DEFAULT NULL");
        db.execSQL("ALTER TABLE recipient ADD COLUMN about_emoji TEXT DEFAULT NULL");
      }

      if (oldVersion < SPLIT_SYSTEM_NAMES) {
        db.execSQL("ALTER TABLE recipient ADD COLUMN system_family_name TEXT DEFAULT NULL");
        db.execSQL("ALTER TABLE recipient ADD COLUMN system_given_name TEXT DEFAULT NULL");
        db.execSQL("UPDATE recipient SET system_given_name = system_display_name");
      }

      if (oldVersion < PAYMENTS) {
        db.execSQL("CREATE TABLE payments(_id INTEGER PRIMARY KEY, " +
                   "uuid TEXT DEFAULT NULL, " +
                   "recipient INTEGER DEFAULT 0, " +
                   "recipient_address TEXT DEFAULT NULL, " +
                   "timestamp INTEGER, " +
                   "note TEXT DEFAULT NULL, " +
                   "direction INTEGER, " +
                   "state INTEGER, " +
                   "failure_reason INTEGER, " +
                   "amount BLOB NOT NULL, " +
                   "fee BLOB NOT NULL, " +
                   "transaction_record BLOB DEFAULT NULL, " +
                   "receipt BLOB DEFAULT NULL, " +
                   "payment_metadata BLOB DEFAULT NULL, " +
                   "receipt_public_key TEXT DEFAULT NULL, " +
                   "block_index INTEGER DEFAULT 0, " +
                   "block_timestamp INTEGER DEFAULT 0, " +
                   "seen INTEGER, " +
                   "UNIQUE(uuid) ON CONFLICT ABORT)");

        db.execSQL("CREATE INDEX IF NOT EXISTS timestamp_direction_index ON payments (timestamp, direction);");
        db.execSQL("CREATE INDEX IF NOT EXISTS timestamp_index ON payments (timestamp);");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS receipt_public_key_index ON payments (receipt_public_key);");
      }

      if (oldVersion < CLEAN_STORAGE_IDS) {
        ContentValues values = new ContentValues();
        values.putNull("storage_service_key");
        int count = db.update("recipient", values, "storage_service_key NOT NULL AND ((phone NOT NULL AND INSTR(phone, '+') = 0) OR (group_id NOT NULL AND (LENGTH(group_id) != 85 and LENGTH(group_id) != 53)))", null);
        Log.i(TAG, "There were " + count + " bad rows that had their storageID removed.");
      }

      if (oldVersion < MP4_GIF_SUPPORT) {
        db.execSQL("ALTER TABLE part ADD COLUMN video_gif INTEGER DEFAULT 0");
      }

      if (oldVersion < BLUR_AVATARS) {
        db.execSQL("ALTER TABLE recipient ADD COLUMN extras BLOB DEFAULT NULL");
        db.execSQL("ALTER TABLE recipient ADD COLUMN groups_in_common INTEGER DEFAULT 0");

        String secureOutgoingSms = "EXISTS(SELECT 1 FROM sms WHERE thread_id = t._id AND (type & 31) = 23 AND (type & 10485760) AND (type & 131072 = 0))";
        String secureOutgoingMms = "EXISTS(SELECT 1 FROM mms WHERE thread_id = t._id AND (msg_box & 31) = 23 AND (msg_box & 10485760) AND (msg_box & 131072 = 0))";

        String selectIdsToUpdateProfileSharing = "SELECT r._id FROM recipient AS r INNER JOIN thread AS t ON r._id = t.recipient_ids WHERE profile_sharing = 0 AND (" + secureOutgoingSms + " OR " + secureOutgoingMms + ")";

        db.rawExecSQL("UPDATE recipient SET profile_sharing = 1 WHERE _id IN (" + selectIdsToUpdateProfileSharing + ")");

        String selectIdsWithGroupsInCommon = "SELECT r._id FROM recipient AS r WHERE EXISTS("
                                             + "SELECT 1 FROM groups AS g INNER JOIN recipient AS gr ON (g.recipient_id = gr._id AND gr.profile_sharing = 1) WHERE g.active = 1 AND (g.members LIKE r._id || ',%' OR g.members LIKE '%,' || r._id || ',%' OR g.members LIKE '%,' || r._id)"
                                             + ")";
        db.rawExecSQL("UPDATE recipient SET groups_in_common = 1 WHERE _id IN (" + selectIdsWithGroupsInCommon + ")");
      }

      if (oldVersion < CLEAN_STORAGE_IDS_WITHOUT_INFO) {
        ContentValues values = new ContentValues();
        values.putNull("storage_service_key");
        int count = db.update("recipient", values, "storage_service_key NOT NULL AND phone IS NULL AND uuid IS NULL AND group_id IS NULL", null);
        Log.i(TAG, "There were " + count + " bad rows that had their storageID removed due to not having any other identifier.");
      }

      if (oldVersion < CLEAN_REACTION_NOTIFICATIONS) {
        ContentValues values = new ContentValues(1);
        values.put("notified", 1);

        int count = 0;
        count += db.update("sms", values, "notified = 0 AND read = 1 AND reactions_unread = 1 AND NOT ((type & 31) = 23 AND (type & 10485760) AND (type & 131072 = 0))", null);
        count += db.update("mms", values, "notified = 0 AND read = 1 AND reactions_unread = 1 AND NOT ((msg_box & 31) = 23 AND (msg_box & 10485760) AND (msg_box & 131072 = 0))", null);
        Log.d(TAG, "Resetting notified for " + count + " read incoming messages that were incorrectly flipped when receiving reactions");

        List<Long> smsIds = new ArrayList<>();

        try (Cursor cursor = db.query("sms", new String[]{"_id", "reactions", "notified_timestamp"}, "notified = 0 AND reactions_unread = 1", null, null, null, null)) {
          while (cursor.moveToNext()) {
            byte[] reactions         = cursor.getBlob(cursor.getColumnIndexOrThrow("reactions"));
            long   notifiedTimestamp = cursor.getLong(cursor.getColumnIndexOrThrow("notified_timestamp"));

            if (reactions == null) {
              continue;
            }

            try {
              boolean hasReceiveLaterThanNotified = ReactionList.parseFrom(reactions)
                                                                .getReactionsList()
                                                                .stream()
                                                                .anyMatch(r -> r.getReceivedTime() > notifiedTimestamp);
              if (!hasReceiveLaterThanNotified) {
                smsIds.add(cursor.getLong(cursor.getColumnIndexOrThrow("_id")));
              }
            } catch (InvalidProtocolBufferException e) {
              Log.e(TAG, e);
            }
          }
        }

        if (smsIds.size() > 0) {
          Log.d(TAG, "Updating " + smsIds.size() + " records in sms");
          db.execSQL("UPDATE sms SET reactions_last_seen = notified_timestamp WHERE _id in (" + Util.join(smsIds, ",") + ")");
        }

        List<Long> mmsIds = new ArrayList<>();

        try (Cursor cursor = db.query("mms", new String[]{"_id", "reactions", "notified_timestamp"}, "notified = 0 AND reactions_unread = 1", null, null, null, null)) {
          while (cursor.moveToNext()) {
            byte[] reactions         = cursor.getBlob(cursor.getColumnIndexOrThrow("reactions"));
            long   notifiedTimestamp = cursor.getLong(cursor.getColumnIndexOrThrow("notified_timestamp"));

            if (reactions == null) {
              continue;
            }

            try {
              boolean hasReceiveLaterThanNotified = ReactionList.parseFrom(reactions)
                                                                .getReactionsList()
                                                                .stream()
                                                                .anyMatch(r -> r.getReceivedTime() > notifiedTimestamp);
              if (!hasReceiveLaterThanNotified) {
                mmsIds.add(cursor.getLong(cursor.getColumnIndexOrThrow("_id")));
              }
            } catch (InvalidProtocolBufferException e) {
              Log.e(TAG, e);
            }
          }
        }

        if (mmsIds.size() > 0) {
          Log.d(TAG, "Updating " + mmsIds.size() + " records in mms");
          db.execSQL("UPDATE mms SET reactions_last_seen = notified_timestamp WHERE _id in (" + Util.join(mmsIds, ",") + ")");
        }
      }

      if (oldVersion < STORAGE_SERVICE_REFACTOR) {
        int deleteCount;
        int insertCount;
        int updateCount;
        int dirtyCount;

        ContentValues deleteValues = new ContentValues();
        deleteValues.putNull("storage_service_key");
        deleteCount = db.update("recipient", deleteValues, "storage_service_key NOT NULL AND (dirty = 3 OR group_type = 1 OR (group_type = 0 AND registered = 2))", null);

        try (Cursor cursor = db.query("recipient", new String[]{"_id"}, "storage_service_key IS NULL AND (dirty = 2 OR registered = 1)", null, null, null, null)) {
          insertCount = cursor.getCount();

          while (cursor.moveToNext()) {
            ContentValues insertValues = new ContentValues();
            insertValues.put("storage_service_key", Base64.encodeBytes(StorageSyncHelper.generateKey()));

            long id = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
            db.update("recipient", insertValues, "_id = ?", SqlUtil.buildArgs(id));
          }
        }

        try (Cursor cursor = db.query("recipient", new String[]{"_id"}, "storage_service_key NOT NULL AND dirty = 1", null, null, null, null)) {
          updateCount = cursor.getCount();

          while (cursor.moveToNext()) {
            ContentValues updateValues = new ContentValues();
            updateValues.put("storage_service_key", Base64.encodeBytes(StorageSyncHelper.generateKey()));

            long id = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
            db.update("recipient", updateValues, "_id = ?", SqlUtil.buildArgs(id));
          }
        }

        ContentValues clearDirtyValues = new ContentValues();
        clearDirtyValues.put("dirty", 0);
        dirtyCount = db.update("recipient", clearDirtyValues, "dirty != 0", null);

        Log.d(TAG, String.format(Locale.US, "For storage service refactor migration, there were %d inserts, %d updated, and %d deletes. Cleared the dirty status on %d rows.", insertCount, updateCount, deleteCount, dirtyCount));
      }

      if (oldVersion < CLEAR_MMS_STORAGE_IDS) {
        ContentValues deleteValues = new ContentValues();
        deleteValues.putNull("storage_service_key");

        int deleteCount = db.update("recipient", deleteValues, "storage_service_key NOT NULL AND (group_type = 1 OR (group_type = 0 AND phone IS NULL AND uuid IS NULL))", null);

        Log.d(TAG, "Cleared storageIds from " + deleteCount + " rows. They were either MMS groups or empty contacts.");
      }

      if (oldVersion < SERVER_GUID) {
        db.execSQL("ALTER TABLE sms ADD COLUMN server_guid TEXT DEFAULT NULL");
        db.execSQL("ALTER TABLE mms ADD COLUMN server_guid TEXT DEFAULT NULL");
      }

      if (oldVersion < CHAT_COLORS) {
        db.execSQL("ALTER TABLE recipient ADD COLUMN chat_colors BLOB DEFAULT NULL");
        db.execSQL("ALTER TABLE recipient ADD COLUMN custom_chat_colors_id INTEGER DEFAULT 0");
        db.execSQL("CREATE TABLE chat_colors (" +
                   "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                   "chat_colors BLOB)");

        Set<Map.Entry<MaterialColor, ChatColors>> entrySet = ChatColorsMapper.getEntrySet();
        String                                    where    = "color = ? AND group_id is NULL";

        for (Map.Entry<MaterialColor, ChatColors> entry : entrySet) {
          String[]      whereArgs = SqlUtil.buildArgs(entry.getKey().serialize());
          ContentValues values    = new ContentValues(2);

          values.put("chat_colors", entry.getValue().serialize().toByteArray());
          values.put("custom_chat_colors_id", entry.getValue().getId().getLongValue());

          db.update("recipient", values, where, whereArgs);
        }
      }

      if (oldVersion < AVATAR_COLORS) {
        try (Cursor cursor  = db.query("recipient", new String[] { "_id" }, "color IS NULL", null, null, null, null)) {
          while (cursor.moveToNext()) {
            long id = cursor.getInt(cursor.getColumnIndexOrThrow("_id"));

            ContentValues values = new ContentValues(1);
            values.put("color", AvatarColor.random().serialize());

            db.update("recipient", values, "_id = ?", new String[] { String.valueOf(id) });
          }
        }
      }

      if (oldVersion < EMOJI_SEARCH) {
        db.execSQL("CREATE VIRTUAL TABLE emoji_search USING fts5(label, emoji UNINDEXED)");
      }

      if (oldVersion < SENDER_KEY && !SqlUtil.tableExists(db, "sender_keys")) {
        db.execSQL("CREATE TABLE sender_keys (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                             "recipient_id INTEGER NOT NULL, " +
                                             "device INTEGER NOT NULL, " +
                                             "distribution_id TEXT NOT NULL, " +
                                             "record BLOB NOT NULL, " +
                                             "created_at INTEGER NOT NULL, " +
                                             "UNIQUE(recipient_id, device, distribution_id) ON CONFLICT REPLACE)");

        db.execSQL("CREATE TABLE sender_key_shared (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                                   "distribution_id TEXT NOT NULL, " +
                                                   "address TEXT NOT NULL, " +
                                                   "device INTEGER NOT NULL, " +
                                                   "UNIQUE(distribution_id, address, device) ON CONFLICT REPLACE)");

        db.execSQL("CREATE TABLE pending_retry_receipts (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                                        "author TEXT NOT NULL, " +
                                                        "device INTEGER NOT NULL, " +
                                                        "sent_timestamp INTEGER NOT NULL, " +
                                                        "received_timestamp TEXT NOT NULL, " +
                                                        "thread_id INTEGER NOT NULL, " +
                                                        "UNIQUE(author, sent_timestamp) ON CONFLICT REPLACE);");

        db.execSQL("ALTER TABLE groups ADD COLUMN distribution_id TEXT DEFAULT NULL");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS group_distribution_id_index ON groups (distribution_id)");

        try (Cursor cursor = db.query("groups", new String[] { "group_id" }, "LENGTH(group_id) = 85", null, null, null, null)) {
          while (cursor.moveToNext()) {
            String        groupId = cursor.getString(cursor.getColumnIndexOrThrow("group_id"));
            ContentValues values  = new ContentValues();

            values.put("distribution_id", DistributionId.create().toString());

            db.update("groups", values, "group_id = ?", new String[] { groupId });
          }
        }
      }

      if (oldVersion < MESSAGE_DUPE_INDEX) {
        db.execSQL("DROP INDEX sms_date_sent_index");
        db.execSQL("CREATE INDEX sms_date_sent_index on sms(date_sent, address, thread_id)");

        db.execSQL("DROP INDEX mms_date_sent_index");
        db.execSQL("CREATE INDEX mms_date_sent_index on mms(date, address, thread_id)");
      }

      if (oldVersion < MESSAGE_LOG) {
        db.execSQL("CREATE TABLE message_send_log (_id INTEGER PRIMARY KEY, " +
                                                  "date_sent INTEGER NOT NULL, " +
                                                  "content BLOB NOT NULL, " +
                                                  "related_message_id INTEGER DEFAULT -1, " +
                                                  "is_related_message_mms INTEGER DEFAULT 0, " +
                                                  "content_hint INTEGER NOT NULL, " +
                                                  "group_id BLOB DEFAULT NULL)");

        db.execSQL("CREATE INDEX message_log_date_sent_index ON message_send_log (date_sent)");
        db.execSQL("CREATE INDEX message_log_related_message_index ON message_send_log (related_message_id, is_related_message_mms)");

        db.execSQL("CREATE TRIGGER msl_sms_delete AFTER DELETE ON sms BEGIN DELETE FROM message_send_log WHERE related_message_id = old._id AND is_related_message_mms = 0; END");
        db.execSQL("CREATE TRIGGER msl_mms_delete AFTER DELETE ON mms BEGIN DELETE FROM message_send_log WHERE related_message_id = old._id AND is_related_message_mms = 1; END");

        db.execSQL("CREATE TABLE message_send_log_recipients (_id INTEGER PRIMARY KEY, " +
                                                             "message_send_log_id INTEGER NOT NULL REFERENCES message_send_log (_id) ON DELETE CASCADE, " +
                                                             "recipient_id INTEGER NOT NULL, " +
                                                             "device INTEGER NOT NULL)");

        db.execSQL("CREATE INDEX message_send_log_recipients_recipient_index ON message_send_log_recipients (recipient_id, device)");
      }

      if (oldVersion < MESSAGE_LOG_2) {
        db.execSQL("DROP TABLE message_send_log");
        db.execSQL("DROP INDEX IF EXISTS message_log_date_sent_index");
        db.execSQL("DROP INDEX IF EXISTS message_log_related_message_index");
        db.execSQL("DROP TRIGGER msl_sms_delete");
        db.execSQL("DROP TRIGGER msl_mms_delete");
        db.execSQL("DROP TABLE message_send_log_recipients");
        db.execSQL("DROP INDEX IF EXISTS message_send_log_recipients_recipient_index");

        db.execSQL("CREATE TABLE msl_payload (_id INTEGER PRIMARY KEY, " +
                                             "date_sent INTEGER NOT NULL, " +
                                             "content BLOB NOT NULL, " +
                                             "content_hint INTEGER NOT NULL)");

        db.execSQL("CREATE INDEX msl_payload_date_sent_index ON msl_payload (date_sent)");

        db.execSQL("CREATE TABLE msl_recipient (_id INTEGER PRIMARY KEY, " +
                                               "payload_id INTEGER NOT NULL REFERENCES msl_payload (_id) ON DELETE CASCADE, " +
                                               "recipient_id INTEGER NOT NULL, " +
                                               "device INTEGER NOT NULL)");

        db.execSQL("CREATE INDEX msl_recipient_recipient_index ON msl_recipient (recipient_id, device, payload_id)");
        db.execSQL("CREATE INDEX msl_recipient_payload_index ON msl_recipient (payload_id)");

        db.execSQL("CREATE TABLE msl_message (_id INTEGER PRIMARY KEY, " +
                                             "payload_id INTEGER NOT NULL REFERENCES msl_payload (_id) ON DELETE CASCADE, " +
                                             "message_id INTEGER NOT NULL, " +
                                             "is_mms INTEGER NOT NULL)");

        db.execSQL("CREATE INDEX msl_message_message_index ON msl_message (message_id, is_mms, payload_id)");

        db.execSQL("CREATE TRIGGER msl_sms_delete AFTER DELETE ON sms BEGIN DELETE FROM msl_payload WHERE _id IN (SELECT payload_id FROM msl_message WHERE message_id = old._id AND is_mms = 0); END");
        db.execSQL("CREATE TRIGGER msl_mms_delete AFTER DELETE ON mms BEGIN DELETE FROM msl_payload WHERE _id IN (SELECT payload_id FROM msl_message WHERE message_id = old._id AND is_mms = 1); END");
        db.execSQL("CREATE TRIGGER msl_attachment_delete AFTER DELETE ON part BEGIN DELETE FROM msl_payload WHERE _id IN (SELECT payload_id FROM msl_message WHERE message_id = old.mid AND is_mms = 1); END");
      }

      if (oldVersion < ABANDONED_MESSAGE_CLEANUP) {
        long start = System.currentTimeMillis();
        int smsDeleteCount = db.delete("sms", "thread_id NOT IN (SELECT _id FROM thread)", null);
        int mmsDeleteCount = db.delete("mms", "thread_id NOT IN (SELECT _id FROM thread)", null);
        Log.i(TAG, "Deleted " + smsDeleteCount + " sms and " + mmsDeleteCount + " mms in " + (System.currentTimeMillis() - start) + " ms");
      }

      if (oldVersion < THREAD_AUTOINCREMENT) {
        Stopwatch stopwatch = new Stopwatch("thread-autoincrement");

        db.execSQL("CREATE TABLE thread_tmp (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                            "date INTEGER DEFAULT 0, " +
                                            "thread_recipient_id INTEGER, "  +
                                            "message_count INTEGER DEFAULT 0, " +
                                            "snippet TEXT, " +
                                            "snippet_charset INTEGER DEFAULT 0, " +
                                            "snippet_type INTEGER DEFAULT 0, " +
                                            "snippet_uri TEXT DEFAULT NULL, " +
                                            "snippet_content_type INTEGER DEFAULT NULL, " +
                                            "snippet_extras TEXT DEFAULT NULL, " +
                                            "read INTEGER DEFAULT 1, "  +
                                            "type INTEGER DEFAULT 0, " +
                                            "error INTEGER DEFAULT 0, " +
                                            "archived INTEGER DEFAULT 0, " +
                                            "status INTEGER DEFAULT 0, " +
                                            "expires_in INTEGER DEFAULT 0, " +
                                            "last_seen INTEGER DEFAULT 0, " +
                                            "has_sent INTEGER DEFAULT 0, " +
                                            "delivery_receipt_count INTEGER DEFAULT 0, " +
                                            "read_receipt_count INTEGER DEFAULT 0, " +
                                            "unread_count INTEGER DEFAULT 0, " +
                                            "last_scrolled INTEGER DEFAULT 0, " +
                                            "pinned INTEGER DEFAULT 0)");
        stopwatch.split("table-create");

        db.execSQL("INSERT INTO thread_tmp SELECT _id, " +
                                                 "date, " +
                                                 "recipient_ids, " +
                                                 "message_count, " +
                                                 "snippet, " +
                                                 "snippet_cs, " +
                                                 "snippet_type, " +
                                                 "snippet_uri, " +
                                                 "snippet_content_type, " +
                                                 "snippet_extras, " +
                                                 "read, " +
                                                 "type, " +
                                                 "error, " +
                                                 "archived, " +
                                                 "status, " +
                                                 "expires_in, " +
                                                 "last_seen, " +
                                                 "has_sent, " +
                                                 "delivery_receipt_count, " +
                                                 "read_receipt_count, " +
                                                 "unread_count, " +
                                                 "last_scrolled, " +
                                                 "pinned " +
                   "FROM thread");

        stopwatch.split("table-copy");

        db.execSQL("DROP TABLE thread");
        db.execSQL("ALTER TABLE thread_tmp RENAME TO thread");

        stopwatch.split("table-rename");

        db.execSQL("CREATE INDEX thread_recipient_id_index ON thread (thread_recipient_id)");
        db.execSQL("CREATE INDEX archived_count_index ON thread (archived, message_count)");
        db.execSQL("CREATE INDEX thread_pinned_index ON thread (pinned)");

        stopwatch.split("indexes");

        db.execSQL("DELETE FROM remapped_threads");

        stopwatch.split("delete-remap");

        stopwatch.stop(TAG);
      }

      if (oldVersion < MMS_AUTOINCREMENT) {
        Stopwatch mmsStopwatch = new Stopwatch("mms-autoincrement");

        db.execSQL("CREATE TABLE mms_tmp (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                         "thread_id INTEGER, " +
                                         "date INTEGER, " +
                                         "date_received INTEGER, " +
                                         "date_server INTEGER DEFAULT -1, " +
                                         "msg_box INTEGER, " +
                                         "read INTEGER DEFAULT 0, " +
                                         "body TEXT, " +
                                         "part_count INTEGER, " +
                                         "ct_l TEXT, " +
                                         "address INTEGER, " +
                                         "address_device_id INTEGER, " +
                                         "exp INTEGER, " +
                                         "m_type INTEGER, " +
                                         "m_size INTEGER, " +
                                         "st INTEGER, " +
                                         "tr_id TEXT, " +
                                         "delivery_receipt_count INTEGER DEFAULT 0, " +
                                         "mismatched_identities TEXT DEFAULT NULL, " +
                                         "network_failures TEXT DEFAULT NULL, " +
                                         "subscription_id INTEGER DEFAULT -1, " +
                                         "expires_in INTEGER DEFAULT 0, " +
                                         "expire_started INTEGER DEFAULT 0, " +
                                         "notified INTEGER DEFAULT 0, " +
                                         "read_receipt_count INTEGER DEFAULT 0, " +
                                         "quote_id INTEGER DEFAULT 0, " +
                                         "quote_author TEXT, " +
                                         "quote_body TEXT, " +
                                         "quote_attachment INTEGER DEFAULT -1, " +
                                         "quote_missing INTEGER DEFAULT 0, " +
                                         "quote_mentions BLOB DEFAULT NULL, " +
                                         "shared_contacts TEXT, " +
                                         "unidentified INTEGER DEFAULT 0, " +
                                         "previews TEXT, " +
                                         "reveal_duration INTEGER DEFAULT 0, " +
                                         "reactions BLOB DEFAULT NULL, " +
                                         "reactions_unread INTEGER DEFAULT 0, " +
                                         "reactions_last_seen INTEGER DEFAULT -1, " +
                                         "remote_deleted INTEGER DEFAULT 0, " +
                                         "mentions_self INTEGER DEFAULT 0, " +
                                         "notified_timestamp INTEGER DEFAULT 0, " +
                                         "viewed_receipt_count INTEGER DEFAULT 0, " +
                                         "server_guid TEXT DEFAULT NULL);");

        mmsStopwatch.split("table-create");

        db.execSQL("INSERT INTO mms_tmp SELECT _id, " +
                                              "thread_id, " +
                                              "date, " +
                                              "date_received, " +
                                              "date_server, " +
                                              "msg_box, " +
                                              "read, " +
                                              "body, " +
                                              "part_count, " +
                                              "ct_l, " +
                                              "address, " +
                                              "address_device_id, " +
                                              "exp, " +
                                              "m_type, " +
                                              "m_size, " +
                                              "st, " +
                                              "tr_id, " +
                                              "delivery_receipt_count, " +
                                              "mismatched_identities, " +
                                              "network_failures, " +
                                              "subscription_id, " +
                                              "expires_in, " +
                                              "expire_started, " +
                                              "notified, " +
                                              "read_receipt_count, " +
                                              "quote_id, " +
                                              "quote_author, " +
                                              "quote_body, " +
                                              "quote_attachment, " +
                                              "quote_missing, " +
                                              "quote_mentions, " +
                                              "shared_contacts, " +
                                              "unidentified, " +
                                              "previews, " +
                                              "reveal_duration, " +
                                              "reactions, " +
                                              "reactions_unread, " +
                                              "reactions_last_seen, " +
                                              "remote_deleted, " +
                                              "mentions_self, " +
                                              "notified_timestamp, " +
                                              "viewed_receipt_count, " +
                                              "server_guid " +
                   "FROM mms");

        mmsStopwatch.split("table-copy");

        db.execSQL("DROP TABLE mms");
        db.execSQL("ALTER TABLE mms_tmp RENAME TO mms");

        mmsStopwatch.split("table-rename");

        db.execSQL("CREATE INDEX mms_read_and_notified_and_thread_id_index ON mms(read, notified, thread_id)");
        db.execSQL("CREATE INDEX mms_message_box_index ON mms (msg_box)");
        db.execSQL("CREATE INDEX mms_date_sent_index ON mms (date, address, thread_id)");
        db.execSQL("CREATE INDEX mms_date_server_index ON mms (date_server)");
        db.execSQL("CREATE INDEX mms_thread_date_index ON mms (thread_id, date_received)");
        db.execSQL("CREATE INDEX mms_reactions_unread_index ON mms (reactions_unread)");

        mmsStopwatch.split("indexes");

        db.execSQL("CREATE TRIGGER mms_ai AFTER INSERT ON mms BEGIN INSERT INTO mms_fts(rowid, body, thread_id) VALUES (new._id, new.body, new.thread_id); END");
        db.execSQL("CREATE TRIGGER mms_ad AFTER DELETE ON mms BEGIN INSERT INTO mms_fts(mms_fts, rowid, body, thread_id) VALUES('delete', old._id, old.body, old.thread_id); END");
        db.execSQL("CREATE TRIGGER mms_au AFTER UPDATE ON mms BEGIN INSERT INTO mms_fts(mms_fts, rowid, body, thread_id) VALUES('delete', old._id, old.body, old.thread_id); INSERT INTO mms_fts(rowid, body, thread_id) VALUES (new._id, new.body, new.thread_id); END");
        db.execSQL("CREATE TRIGGER msl_mms_delete AFTER DELETE ON mms BEGIN DELETE FROM msl_payload WHERE _id IN (SELECT payload_id FROM msl_message WHERE message_id = old._id AND is_mms = 1); END");

        mmsStopwatch.split("triggers");
        mmsStopwatch.stop(TAG);

        Stopwatch smsStopwatch = new Stopwatch("sms-autoincrement");

        db.execSQL("CREATE TABLE sms_tmp (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                         "thread_id INTEGER, " +
                                         "address INTEGER, " +
                                         "address_device_id INTEGER DEFAULT 1, " +
                                         "person INTEGER, " +
                                         "date INTEGER, " +
                                         "date_sent INTEGER, " +
                                         "date_server INTEGER DEFAULT -1, " +
                                         "protocol INTEGER, " +
                                         "read INTEGER DEFAULT 0, " +
                                         "status INTEGER DEFAULT -1, " +
                                         "type INTEGER, " +
                                         "reply_path_present INTEGER, " +
                                         "delivery_receipt_count INTEGER DEFAULT 0, " +
                                         "subject TEXT, " +
                                         "body TEXT, " +
                                         "mismatched_identities TEXT DEFAULT NULL, " +
                                         "service_center TEXT, " +
                                         "subscription_id INTEGER DEFAULT -1, " +
                                         "expires_in INTEGER DEFAULT 0, " +
                                         "expire_started INTEGER DEFAULT 0, " +
                                         "notified DEFAULT 0, " +
                                         "read_receipt_count INTEGER DEFAULT 0, " +
                                         "unidentified INTEGER DEFAULT 0, " +
                                         "reactions BLOB DEFAULT NULL, " +
                                         "reactions_unread INTEGER DEFAULT 0, " +
                                         "reactions_last_seen INTEGER DEFAULT -1, " +
                                         "remote_deleted INTEGER DEFAULT 0, " +
                                         "notified_timestamp INTEGER DEFAULT 0, " +
                                         "server_guid TEXT DEFAULT NULL)");

        smsStopwatch.split("table-create");

        db.execSQL("INSERT INTO sms_tmp SELECT _id, " +
                                              "thread_id, " +
                                              "address, " +
                                              "address_device_id, " +
                                              "person, " +
                                              "date, " +
                                              "date_sent, " +
                                              "date_server , " +
                                              "protocol, " +
                                              "read, " +
                                              "status , " +
                                              "type, " +
                                              "reply_path_present, " +
                                              "delivery_receipt_count, " +
                                              "subject, " +
                                              "body, " +
                                              "mismatched_identities, " +
                                              "service_center, " +
                                              "subscription_id , " +
                                              "expires_in, " +
                                              "expire_started, " +
                                              "notified, " +
                                              "read_receipt_count, " +
                                              "unidentified, " +
                                              "reactions BLOB, " +
                                              "reactions_unread, " +
                                              "reactions_last_seen , " +
                                              "remote_deleted, " +
                                              "notified_timestamp, " +
                                              "server_guid " +
                   "FROM sms");

        smsStopwatch.split("table-copy");

        db.execSQL("DROP TABLE sms");
        db.execSQL("ALTER TABLE sms_tmp RENAME TO sms");

        smsStopwatch.split("table-rename");

        db.execSQL("CREATE INDEX sms_read_and_notified_and_thread_id_index ON sms(read, notified, thread_id)");
        db.execSQL("CREATE INDEX sms_type_index ON sms (type)");
        db.execSQL("CREATE INDEX sms_date_sent_index ON sms (date_sent, address, thread_id)");
        db.execSQL("CREATE INDEX sms_date_server_index ON sms (date_server)");
        db.execSQL("CREATE INDEX sms_thread_date_index ON sms (thread_id, date)");
        db.execSQL("CREATE INDEX sms_reactions_unread_index ON sms (reactions_unread)");

        smsStopwatch.split("indexes");

        db.execSQL("CREATE TRIGGER sms_ai AFTER INSERT ON sms BEGIN INSERT INTO sms_fts(rowid, body, thread_id) VALUES (new._id, new.body, new.thread_id); END;");
        db.execSQL("CREATE TRIGGER sms_ad AFTER DELETE ON sms BEGIN INSERT INTO sms_fts(sms_fts, rowid, body, thread_id) VALUES('delete', old._id, old.body, old.thread_id); END;");
        db.execSQL("CREATE TRIGGER sms_au AFTER UPDATE ON sms BEGIN INSERT INTO sms_fts(sms_fts, rowid, body, thread_id) VALUES('delete', old._id, old.body, old.thread_id); INSERT INTO sms_fts(rowid, body, thread_id) VALUES(new._id, new.body, new.thread_id); END;");
        db.execSQL("CREATE TRIGGER msl_sms_delete AFTER DELETE ON sms BEGIN DELETE FROM msl_payload WHERE _id IN (SELECT payload_id FROM msl_message WHERE message_id = old._id AND is_mms = 0); END");

        smsStopwatch.split("triggers");
        smsStopwatch.stop(TAG);
      }

      if (oldVersion < ABANDONED_ATTACHMENT_CLEANUP) {
        db.delete("part", "mid != -8675309 AND mid NOT IN (SELECT _id FROM mms)", null);
      }

      if (oldVersion < AVATAR_PICKER) {
        db.execSQL("CREATE TABLE avatar_picker (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                               "last_used INTEGER DEFAULT 0, " +
                                               "group_id TEXT DEFAULT NULL, " +
                                               "avatar BLOB NOT NULL)");

        try (Cursor cursor = db.query("recipient", new String[] { "_id" }, "color IS NULL", null, null, null, null)) {
          while (cursor.moveToNext()) {
            long id = cursor.getInt(cursor.getColumnIndexOrThrow("_id"));

            ContentValues values = new ContentValues(1);
            values.put("color", AvatarColor.random().serialize());

            db.update("recipient", values, "_id = ?", new String[] { String.valueOf(id) });
          }
        }
      }

      if (oldVersion < THREAD_CLEANUP) {
        db.delete("mms", "thread_id NOT IN (SELECT _id FROM thread)", null);
        db.delete("part", "mid != -8675309 AND mid NOT IN (SELECT _id FROM mms)", null);
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }

    if (oldVersion < MIGRATE_PREKEYS_VERSION) {
      PreKeyMigrationHelper.cleanUpPreKeys(context);
    }

    Log.i(TAG, "Upgrade complete. Took " + (System.currentTimeMillis() - startTime) + " ms.");
  }

  public org.thoughtcrime.securesms.database.SQLiteDatabase getReadableDatabase() {
    return new org.thoughtcrime.securesms.database.SQLiteDatabase(getReadableDatabase(databaseSecret.asString()));
  }

  public org.thoughtcrime.securesms.database.SQLiteDatabase getWritableDatabase() {
    return new org.thoughtcrime.securesms.database.SQLiteDatabase(getWritableDatabase(databaseSecret.asString()));
  }

  @Override
  public @NonNull SQLiteDatabase getSqlCipherDatabase() {
    return getWritableDatabase().getSqlCipherDatabase();
  }

  public void markCurrent(SQLiteDatabase db) {
    db.setVersion(DATABASE_VERSION);
  }

  public static boolean databaseFileExists(@NonNull Context context) {
    return context.getDatabasePath(DATABASE_NAME).exists();
  }

  public static File getDatabaseFile(@NonNull Context context) {
    return context.getDatabasePath(DATABASE_NAME);
  }

  private void executeStatements(SQLiteDatabase db, String[] statements) {
    for (String statement : statements)
      db.execSQL(statement);
  }
}
