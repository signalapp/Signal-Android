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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import com.google.i18n.phonenumbers.ShortNumberInfo;

import org.thoughtcrime.securesms.DatabaseUpgradeActivity;
import org.thoughtcrime.securesms.contacts.ContactsDatabase;
import org.thoughtcrime.securesms.crypto.DecryptingPartInputStream;
import org.thoughtcrime.securesms.crypto.MasterCipher;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.JsonUtils;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.InvalidMessageException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DatabaseFactory {

  private static final int INTRODUCED_IDENTITIES_VERSION                   = 2;
  private static final int INTRODUCED_INDEXES_VERSION                      = 3;
  private static final int INTRODUCED_DATE_SENT_VERSION                    = 4;
  private static final int INTRODUCED_DRAFTS_VERSION                       = 5;
  private static final int INTRODUCED_NEW_TYPES_VERSION                    = 6;
  private static final int INTRODUCED_MMS_BODY_VERSION                     = 7;
  private static final int INTRODUCED_MMS_FROM_VERSION                     = 8;
  private static final int INTRODUCED_TOFU_IDENTITY_VERSION                = 9;
  private static final int INTRODUCED_PUSH_DATABASE_VERSION                = 10;
  private static final int INTRODUCED_GROUP_DATABASE_VERSION               = 11;
  private static final int INTRODUCED_PUSH_FIX_VERSION                     = 12;
  private static final int INTRODUCED_DELIVERY_RECEIPTS                    = 13;
  private static final int INTRODUCED_PART_DATA_SIZE_VERSION               = 14;
  private static final int INTRODUCED_THUMBNAILS_VERSION                   = 15;
  private static final int INTRODUCED_IDENTITY_COLUMN_VERSION              = 16;
  private static final int INTRODUCED_UNIQUE_PART_IDS_VERSION              = 17;
  private static final int INTRODUCED_RECIPIENT_PREFS_DB                   = 18;
  private static final int INTRODUCED_ENVELOPE_CONTENT_VERSION             = 19;
  private static final int INTRODUCED_COLOR_PREFERENCE_VERSION             = 20;
  private static final int INTRODUCED_DB_OPTIMIZATIONS_VERSION             = 21;
  private static final int INTRODUCED_INVITE_REMINDERS_VERSION             = 22;
  private static final int INTRODUCED_CONVERSATION_LIST_THUMBNAILS_VERSION = 23;
  private static final int INTRODUCED_ARCHIVE_VERSION                      = 24;
  private static final int INTRODUCED_CONVERSATION_LIST_STATUS_VERSION     = 25;
  private static final int MIGRATED_CONVERSATION_LIST_STATUS_VERSION       = 26;
  private static final int INTRODUCED_SUBSCRIPTION_ID_VERSION              = 27;
  private static final int INTRODUCED_EXPIRE_MESSAGES_VERSION              = 28;
  private static final int INTRODUCED_LAST_SEEN                            = 29;
  private static final int INTRODUCED_DIGEST                               = 30;
  private static final int INTRODUCED_NOTIFIED                             = 31;
  private static final int INTRODUCED_DOCUMENTS                            = 32;
  private static final int INTRODUCED_FAST_PREFLIGHT                       = 33;
  private static final int INTRODUCED_VOICE_NOTES                          = 34;
  private static final int INTRODUCED_IDENTITY_TIMESTAMP                   = 35;
  private static final int SANIFY_ATTACHMENT_DOWNLOAD                      = 36;
  private static final int NO_MORE_CANONICAL_ADDRESS_DATABASE              = 37;
  private static final int DATABASE_VERSION                                = 37;

  private static final String DATABASE_NAME    = "messages.db";
  private static final Object lock             = new Object();

  private static DatabaseFactory instance;

  private DatabaseHelper databaseHelper;

  private final SmsDatabase sms;
  private final EncryptingSmsDatabase encryptingSms;
  private final MmsDatabase mms;
  private final AttachmentDatabase attachments;
  private final MediaDatabase media;
  private final ThreadDatabase thread;
  private final MmsAddressDatabase mmsAddress;
  private final MmsSmsDatabase mmsSmsDatabase;
  private final IdentityDatabase identityDatabase;
  private final DraftDatabase draftDatabase;
  private final PushDatabase pushDatabase;
  private final GroupDatabase groupDatabase;
  private final RecipientPreferenceDatabase recipientPreferenceDatabase;
  private final ContactsDatabase contactsDatabase;

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

  public static EncryptingSmsDatabase getEncryptingSmsDatabase(Context context) {
    return getInstance(context).encryptingSms;
  }

  public static AttachmentDatabase getAttachmentDatabase(Context context) {
    return getInstance(context).attachments;
  }

  public static MediaDatabase getMediaDatabase(Context context) {
    return getInstance(context).media;
  }

  public static MmsAddressDatabase getMmsAddressDatabase(Context context) {
    return getInstance(context).mmsAddress;
  }

  public static IdentityDatabase getIdentityDatabase(Context context) {
    return getInstance(context).identityDatabase;
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

  public static RecipientPreferenceDatabase getRecipientPreferenceDatabase(Context context) {
    return getInstance(context).recipientPreferenceDatabase;
  }

  public static ContactsDatabase getContactsDatabase(Context context) {
    return getInstance(context).contactsDatabase;
  }

  private DatabaseFactory(Context context) {
    this.databaseHelper              = new DatabaseHelper(context, DATABASE_NAME, null, DATABASE_VERSION);
    this.sms                         = new SmsDatabase(context, databaseHelper);
    this.encryptingSms               = new EncryptingSmsDatabase(context, databaseHelper);
    this.mms                         = new MmsDatabase(context, databaseHelper);
    this.attachments                 = new AttachmentDatabase(context, databaseHelper);
    this.media                       = new MediaDatabase(context, databaseHelper);
    this.thread                      = new ThreadDatabase(context, databaseHelper);
    this.mmsAddress                  = new MmsAddressDatabase(context, databaseHelper);
    this.mmsSmsDatabase              = new MmsSmsDatabase(context, databaseHelper);
    this.identityDatabase            = new IdentityDatabase(context, databaseHelper);
    this.draftDatabase               = new DraftDatabase(context, databaseHelper);
    this.pushDatabase                = new PushDatabase(context, databaseHelper);
    this.groupDatabase               = new GroupDatabase(context, databaseHelper);
    this.recipientPreferenceDatabase = new RecipientPreferenceDatabase(context, databaseHelper);
    this.contactsDatabase            = new ContactsDatabase(context);
  }

  public void reset(Context context) {
    DatabaseHelper old = this.databaseHelper;
    this.databaseHelper = new DatabaseHelper(context, DATABASE_NAME, null, DATABASE_VERSION);

    this.sms.reset(databaseHelper);
    this.encryptingSms.reset(databaseHelper);
    this.mms.reset(databaseHelper);
    this.attachments.reset(databaseHelper);
    this.thread.reset(databaseHelper);
    this.mmsAddress.reset(databaseHelper);
    this.mmsSmsDatabase.reset(databaseHelper);
    this.identityDatabase.reset(databaseHelper);
    this.draftDatabase.reset(databaseHelper);
    this.pushDatabase.reset(databaseHelper);
    this.groupDatabase.reset(databaseHelper);
    this.recipientPreferenceDatabase.reset(databaseHelper);
    old.close();
  }

  public void onApplicationLevelUpgrade(Context context, MasterSecret masterSecret, int fromVersion,
                                        DatabaseUpgradeActivity.DatabaseUpgradeListener listener)
  {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.beginTransaction();

    if (fromVersion < DatabaseUpgradeActivity.NO_MORE_KEY_EXCHANGE_PREFIX_VERSION) {
      String KEY_EXCHANGE             = "?TextSecureKeyExchange";
      String PROCESSED_KEY_EXCHANGE   = "?TextSecureKeyExchangd";
      String STALE_KEY_EXCHANGE       = "?TextSecureKeyExchangs";
      int ROW_LIMIT                   = 500;

      MasterCipher masterCipher = new MasterCipher(masterSecret);
      int smsCount              = 0;
      int threadCount           = 0;
      int skip                  = 0;

      Cursor cursor = db.query("sms", new String[] {"COUNT(*)"}, "type & " + 0x80000000 + " != 0",
                               null, null, null, null);

      if (cursor != null && cursor.moveToFirst()) {
        smsCount = cursor.getInt(0);
        cursor.close();
      }

      cursor = db.query("thread", new String[] {"COUNT(*)"}, "snippet_type & " + 0x80000000 + " != 0",
                        null, null, null, null);

      if (cursor != null && cursor.moveToFirst()) {
        threadCount = cursor.getInt(0);
        cursor.close();
      }

      Cursor smsCursor = null;

      Log.w("DatabaseFactory", "Upgrade count: " + (smsCount + threadCount));

      do {
        Log.w("DatabaseFactory", "Looping SMS cursor...");
        if (smsCursor != null)
          smsCursor.close();

        smsCursor = db.query("sms", new String[] {"_id", "type", "body"},
                             "type & " + 0x80000000 + " != 0",
                             null, null, null, "_id", skip + "," + ROW_LIMIT);

        while (smsCursor != null && smsCursor.moveToNext()) {
          listener.setProgress(smsCursor.getPosition() + skip, smsCount + threadCount);

          try {
            String body = masterCipher.decryptBody(smsCursor.getString(smsCursor.getColumnIndexOrThrow("body")));
            long type   = smsCursor.getLong(smsCursor.getColumnIndexOrThrow("type"));
            long id     = smsCursor.getLong(smsCursor.getColumnIndexOrThrow("_id"));

            if (body.startsWith(KEY_EXCHANGE)) {
              body  = body.substring(KEY_EXCHANGE.length());
              body  = masterCipher.encryptBody(body);
              type |= 0x8000;

              db.execSQL("UPDATE sms SET body = ?, type = ? WHERE _id = ?",
                         new String[] {body, type+"", id+""});
            } else if (body.startsWith(PROCESSED_KEY_EXCHANGE)) {
              body  = body.substring(PROCESSED_KEY_EXCHANGE.length());
              body  = masterCipher.encryptBody(body);
              type |= (0x8000 | 0x2000);

              db.execSQL("UPDATE sms SET body = ?, type = ? WHERE _id = ?",
                         new String[] {body, type+"", id+""});
            } else if (body.startsWith(STALE_KEY_EXCHANGE)) {
              body  = body.substring(STALE_KEY_EXCHANGE.length());
              body  = masterCipher.encryptBody(body);
              type |= (0x8000 | 0x4000);

              db.execSQL("UPDATE sms SET body = ?, type = ? WHERE _id = ?",
                         new String[] {body, type+"", id+""});
            }
          } catch (InvalidMessageException e) {
            Log.w("DatabaseFactory", e);
          }
        }

        skip += ROW_LIMIT;
      } while (smsCursor != null && smsCursor.getCount() > 0);



      Cursor threadCursor = null;
      skip                = 0;

      do {
        Log.w("DatabaseFactory", "Looping thread cursor...");

        if (threadCursor != null)
          threadCursor.close();

        threadCursor = db.query("thread", new String[] {"_id", "snippet_type", "snippet"},
                                "snippet_type & " + 0x80000000 + " != 0",
                                null, null, null, "_id", skip + "," + ROW_LIMIT);

        while (threadCursor != null && threadCursor.moveToNext()) {
          listener.setProgress(smsCount + threadCursor.getPosition(), smsCount + threadCount);

          try {
            String snippet   = threadCursor.getString(threadCursor.getColumnIndexOrThrow("snippet"));
            long snippetType = threadCursor.getLong(threadCursor.getColumnIndexOrThrow("snippet_type"));
            long id          = threadCursor.getLong(threadCursor.getColumnIndexOrThrow("_id"));

            if (!TextUtils.isEmpty(snippet)) {
              snippet = masterCipher.decryptBody(snippet);
            }

            if (snippet.startsWith(KEY_EXCHANGE)) {
              snippet      = snippet.substring(KEY_EXCHANGE.length());
              snippet      = masterCipher.encryptBody(snippet);
              snippetType |= 0x8000;

              db.execSQL("UPDATE thread SET snippet = ?, snippet_type = ? WHERE _id = ?",
                         new String[] {snippet, snippetType+"", id+""});
            } else if (snippet.startsWith(PROCESSED_KEY_EXCHANGE)) {
              snippet      = snippet.substring(PROCESSED_KEY_EXCHANGE.length());
              snippet      = masterCipher.encryptBody(snippet);
              snippetType |= (0x8000 | 0x2000);

              db.execSQL("UPDATE thread SET snippet = ?, snippet_type = ? WHERE _id = ?",
                         new String[] {snippet, snippetType+"", id+""});
            } else if (snippet.startsWith(STALE_KEY_EXCHANGE)) {
              snippet      = snippet.substring(STALE_KEY_EXCHANGE.length());
              snippet      = masterCipher.encryptBody(snippet);
              snippetType |= (0x8000 | 0x4000);

              db.execSQL("UPDATE thread SET snippet = ?, snippet_type = ? WHERE _id = ?",
                         new String[] {snippet, snippetType+"", id+""});
            }
          } catch (InvalidMessageException e) {
            Log.w("DatabaseFactory", e);
          }
        }

        skip += ROW_LIMIT;
      } while (threadCursor != null && threadCursor.getCount() > 0);

      if (smsCursor != null)
        smsCursor.close();

      if (threadCursor != null)
        threadCursor.close();
    }

    if (fromVersion < DatabaseUpgradeActivity.MMS_BODY_VERSION) {
      Log.w("DatabaseFactory", "Update MMS bodies...");
      MasterCipher masterCipher = new MasterCipher(masterSecret);
      Cursor mmsCursor          = db.query("mms", new String[] {"_id"},
                                           "msg_box & " + 0x80000000L + " != 0",
                                           null, null, null, null);

      Log.w("DatabaseFactory", "Got MMS rows: " + (mmsCursor == null ? "null" : mmsCursor.getCount()));

      while (mmsCursor != null && mmsCursor.moveToNext()) {
        listener.setProgress(mmsCursor.getPosition(), mmsCursor.getCount());

        long mmsId        = mmsCursor.getLong(mmsCursor.getColumnIndexOrThrow("_id"));
        String body       = null;
        int partCount     = 0;
        Cursor partCursor = db.query("part", new String[] {"_id", "ct", "_data", "encrypted"},
                                     "mid = ?", new String[] {mmsId+""}, null, null, null);

        while (partCursor != null && partCursor.moveToNext()) {
          String contentType = partCursor.getString(partCursor.getColumnIndexOrThrow("ct"));

          if (MediaUtil.isTextType(contentType)) {
            try {
              long partId         = partCursor.getLong(partCursor.getColumnIndexOrThrow("_id"));
              String dataLocation = partCursor.getString(partCursor.getColumnIndexOrThrow("_data"));
              boolean encrypted   = partCursor.getInt(partCursor.getColumnIndexOrThrow("encrypted")) == 1;
              File dataFile       = new File(dataLocation);

              InputStream is;

              if (encrypted) is = DecryptingPartInputStream.createFor(masterSecret, dataFile);
              else           is = new FileInputStream(dataFile);

              body = (body == null) ? Util.readFullyAsString(is) : body + " " + Util.readFullyAsString(is);

              //noinspection ResultOfMethodCallIgnored
              dataFile.delete();
              db.delete("part", "_id = ?", new String[] {partId+""});
            } catch (IOException e) {
              Log.w("DatabaseFactory", e);
            }
          } else if (MediaUtil.isAudioType(contentType) ||
                     MediaUtil.isImageType(contentType) ||
                     MediaUtil.isVideoType(contentType))
          {
            partCount++;
          }
        }

        if (!TextUtils.isEmpty(body)) {
          body = masterCipher.encryptBody(body);
          db.execSQL("UPDATE mms SET body = ?, part_count = ? WHERE _id = ?",
                     new String[] {body, partCount+"", mmsId+""});
        } else {
          db.execSQL("UPDATE mms SET part_count = ? WHERE _id = ?",
                     new String[] {partCount+"", mmsId+""});
        }

        Log.w("DatabaseFactory", "Updated body: " + body + " and part_count: " + partCount);
      }
    }

    if (fromVersion < DatabaseUpgradeActivity.TOFU_IDENTITIES_VERSION) {
      File sessionDirectory = new File(context.getFilesDir() + File.separator + "sessions");

      if (sessionDirectory.exists() && sessionDirectory.isDirectory()) {
        File[] sessions = sessionDirectory.listFiles();

        if (sessions != null) {
          for (File session : sessions) {
            String name = session.getName();

            if (name.matches("[0-9]+")) {
              long        recipientId = Long.parseLong(name);
              IdentityKey identityKey = null;
              // NOTE (4/21/14) -- At this moment in time, we're forgetting the ability to parse
              // V1 session records.  Despite our usual attempts to avoid using shared code in the
              // upgrade path, this is too complex to put here directly.  Thus, unfortunately
              // this operation is now lost to the ages.  From the git log, it seems to have been
              // almost exactly a year since this went in, so hopefully the bulk of people have
              // already upgraded.
//              IdentityKey identityKey     = Session.getRemoteIdentityKey(context, masterSecret, recipientId);

              if (identityKey != null) {
                MasterCipher masterCipher = new MasterCipher(masterSecret);
                String identityKeyString  = Base64.encodeBytes(identityKey.serialize());
                String macString          = Base64.encodeBytes(masterCipher.getMacFor(recipientId +
                                                                                      identityKeyString));

                db.execSQL("REPLACE INTO identities (recipient, key, mac) VALUES (?, ?, ?)",
                           new String[] {recipientId+"", identityKeyString, macString});
              }
            }
          }
        }
      }
    }

    if (fromVersion < DatabaseUpgradeActivity.ASYMMETRIC_MASTER_SECRET_FIX_VERSION) {
      if (!MasterSecretUtil.hasAsymmericMasterSecret(context)) {
        MasterSecretUtil.generateAsymmetricMasterSecret(context, masterSecret);

        MasterCipher masterCipher = new MasterCipher(masterSecret);
        Cursor       cursor       = null;

        try {
          cursor = db.query(SmsDatabase.TABLE_NAME,
                            new String[] {SmsDatabase.ID, SmsDatabase.BODY, SmsDatabase.TYPE},
                            SmsDatabase.TYPE + " & ? == 0",
                            new String[] {String.valueOf(SmsDatabase.Types.ENCRYPTION_MASK)},
                            null, null, null);

          while (cursor.moveToNext()) {
            long   id   = cursor.getLong(0);
            String body = cursor.getString(1);
            long   type = cursor.getLong(2);

            String encryptedBody = masterCipher.encryptBody(body);

            ContentValues update = new ContentValues();
            update.put(SmsDatabase.BODY, encryptedBody);
            update.put(SmsDatabase.TYPE, type | SmsDatabase.Types.ENCRYPTION_SYMMETRIC_BIT);

            db.update(SmsDatabase.TABLE_NAME, update, SmsDatabase.ID  + " = ?",
                      new String[] {String.valueOf(id)});
          }
        } finally {
          if (cursor != null)
            cursor.close();
        }
      }
    }

    db.setTransactionSuccessful();
    db.endTransaction();

//    DecryptingQueue.schedulePendingDecrypts(context, masterSecret);
    MessageNotifier.updateNotification(context, masterSecret);
  }

  private static class DatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = DatabaseHelper.class.getSimpleName();

    private final Context context;

    public DatabaseHelper(Context context, String name, CursorFactory factory, int version) {
      super(context, name, factory, version);
      this.context = context.getApplicationContext();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
      db.execSQL(SmsDatabase.CREATE_TABLE);
      db.execSQL(MmsDatabase.CREATE_TABLE);
      db.execSQL(AttachmentDatabase.CREATE_TABLE);
      db.execSQL(ThreadDatabase.CREATE_TABLE);
      db.execSQL(MmsAddressDatabase.CREATE_TABLE);
      db.execSQL(IdentityDatabase.CREATE_TABLE);
      db.execSQL(DraftDatabase.CREATE_TABLE);
      db.execSQL(PushDatabase.CREATE_TABLE);
      db.execSQL(GroupDatabase.CREATE_TABLE);
      db.execSQL(RecipientPreferenceDatabase.CREATE_TABLE);

      executeStatements(db, SmsDatabase.CREATE_INDEXS);
      executeStatements(db, MmsDatabase.CREATE_INDEXS);
      executeStatements(db, AttachmentDatabase.CREATE_INDEXS);
      executeStatements(db, ThreadDatabase.CREATE_INDEXS);
      executeStatements(db, MmsAddressDatabase.CREATE_INDEXS);
      executeStatements(db, DraftDatabase.CREATE_INDEXS);
      executeStatements(db, GroupDatabase.CREATE_INDEXS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
      db.beginTransaction();

      if (oldVersion < INTRODUCED_IDENTITIES_VERSION) {
        db.execSQL("CREATE TABLE identities (_id INTEGER PRIMARY KEY, key TEXT UNIQUE, name TEXT UNIQUE, mac TEXT);");
      }

      if (oldVersion < INTRODUCED_INDEXES_VERSION) {
        executeStatements(db, new String[] {
            "CREATE INDEX IF NOT EXISTS sms_thread_id_index ON sms (thread_id);",
            "CREATE INDEX IF NOT EXISTS sms_read_index ON sms (read);",
            "CREATE INDEX IF NOT EXISTS sms_read_and_thread_id_index ON sms (read,thread_id);",
            "CREATE INDEX IF NOT EXISTS sms_type_index ON sms (type);"
        });
        executeStatements(db, new String[] {
            "CREATE INDEX IF NOT EXISTS mms_thread_id_index ON mms (thread_id);",
            "CREATE INDEX IF NOT EXISTS mms_read_index ON mms (read);",
            "CREATE INDEX IF NOT EXISTS mms_read_and_thread_id_index ON mms (read,thread_id);",
            "CREATE INDEX IF NOT EXISTS mms_message_box_index ON mms (msg_box);"
        });
        executeStatements(db, new String[] {
            "CREATE INDEX IF NOT EXISTS part_mms_id_index ON part (mid);"
        });
        executeStatements(db, new String[] {
            "CREATE INDEX IF NOT EXISTS thread_recipient_ids_index ON thread (recipient_ids);",
        });
        executeStatements(db, new String[] {
            "CREATE INDEX IF NOT EXISTS mms_addresses_mms_id_index ON mms_addresses (mms_id);",
        });
      }

      if (oldVersion < INTRODUCED_DATE_SENT_VERSION) {
        db.execSQL("ALTER TABLE sms ADD COLUMN date_sent INTEGER;");
        db.execSQL("UPDATE sms SET date_sent = date;");

        db.execSQL("ALTER TABLE mms ADD COLUMN date_received INTEGER;");
        db.execSQL("UPDATE mms SET date_received = date;");
      }

      if (oldVersion < INTRODUCED_DRAFTS_VERSION) {
        db.execSQL("CREATE TABLE drafts (_id INTEGER PRIMARY KEY, thread_id INTEGER, type TEXT, value TEXT);");
        executeStatements(db, new String[] {
            "CREATE INDEX IF NOT EXISTS draft_thread_index ON drafts (thread_id);",
        });
      }

      if (oldVersion < INTRODUCED_NEW_TYPES_VERSION) {
        String KEY_EXCHANGE             = "?TextSecureKeyExchange";
        String SYMMETRIC_ENCRYPT        = "?TextSecureLocalEncrypt";
        String ASYMMETRIC_ENCRYPT       = "?TextSecureAsymmetricEncrypt";
        String ASYMMETRIC_LOCAL_ENCRYPT = "?TextSecureAsymmetricLocalEncrypt";
        String PROCESSED_KEY_EXCHANGE   = "?TextSecureKeyExchangd";
        String STALE_KEY_EXCHANGE       = "?TextSecureKeyExchangs";

        // SMS Updates
        db.execSQL("UPDATE sms SET type = ? WHERE type = ?", new String[] {20L+"", 1L+""});
        db.execSQL("UPDATE sms SET type = ? WHERE type = ?", new String[] {21L+"", 43L+""});
        db.execSQL("UPDATE sms SET type = ? WHERE type = ?", new String[] {22L+"", 4L+""});
        db.execSQL("UPDATE sms SET type = ? WHERE type = ?", new String[] {23L+"", 2L+""});
        db.execSQL("UPDATE sms SET type = ? WHERE type = ?", new String[] {24L+"", 5L+""});

        db.execSQL("UPDATE sms SET type = ? WHERE type = ?", new String[] {(21L | 0x800000L)+"", 42L+""});
        db.execSQL("UPDATE sms SET type = ? WHERE type = ?", new String[] {(23L | 0x800000L)+"", 44L+""});
        db.execSQL("UPDATE sms SET type = ? WHERE type = ?", new String[] {(20L | 0x800000L)+"", 45L+""});
        db.execSQL("UPDATE sms SET type = ? WHERE type = ?", new String[] {(20L | 0x800000L | 0x10000000L)+"", 46L+""});
        db.execSQL("UPDATE sms SET type = ? WHERE type = ?", new String[] {(20L)+"", 47L+""});
        db.execSQL("UPDATE sms SET type = ? WHERE type = ?", new String[] {(20L | 0x800000L | 0x08000000L)+"", 48L+""});

        db.execSQL("UPDATE sms SET body = substr(body, ?), type = type | ? WHERE body LIKE ?",
                   new String[] {(SYMMETRIC_ENCRYPT.length()+1)+"",
                                  0x80000000L+"",
                                  SYMMETRIC_ENCRYPT + "%"});

        db.execSQL("UPDATE sms SET body = substr(body, ?), type = type | ? WHERE body LIKE ?",
                   new String[] {(ASYMMETRIC_LOCAL_ENCRYPT.length()+1)+"",
                                  0x40000000L+"",
                                  ASYMMETRIC_LOCAL_ENCRYPT + "%"});

        db.execSQL("UPDATE sms SET body = substr(body, ?), type = type | ? WHERE body LIKE ?",
                   new String[] {(ASYMMETRIC_ENCRYPT.length()+1)+"",
                                 (0x800000L | 0x20000000L)+"",
                                 ASYMMETRIC_ENCRYPT + "%"});

        db.execSQL("UPDATE sms SET body = substr(body, ?), type = type | ? WHERE body LIKE ?",
                   new String[] {(KEY_EXCHANGE.length()+1)+"",
                                  0x8000L+"",
                                  KEY_EXCHANGE + "%"});

        db.execSQL("UPDATE sms SET body = substr(body, ?), type = type | ? WHERE body LIKE ?",
                   new String[] {(PROCESSED_KEY_EXCHANGE.length()+1)+"",
                                  (0x8000L | 0x2000L)+"",
                                  PROCESSED_KEY_EXCHANGE + "%"});

        db.execSQL("UPDATE sms SET body = substr(body, ?), type = type | ? WHERE body LIKE ?",
                   new String[] {(STALE_KEY_EXCHANGE.length()+1)+"",
                                 (0x8000L | 0x4000L)+"",
                                 STALE_KEY_EXCHANGE + "%"});

        // MMS Updates

        db.execSQL("UPDATE mms SET msg_box = ? WHERE msg_box = ?", new String[] {(20L | 0x80000000L)+"", 1+""});
        db.execSQL("UPDATE mms SET msg_box = ? WHERE msg_box = ?", new String[] {(23L | 0x80000000L)+"", 2+""});
        db.execSQL("UPDATE mms SET msg_box = ? WHERE msg_box = ?", new String[] {(21L | 0x80000000L)+"", 4+""});
        db.execSQL("UPDATE mms SET msg_box = ? WHERE msg_box = ?", new String[] {(24L | 0x80000000L)+"", 12+""});

        db.execSQL("UPDATE mms SET msg_box = ? WHERE msg_box = ?", new String[] {(21L | 0x80000000L | 0x800000L) +"", 5+""});
        db.execSQL("UPDATE mms SET msg_box = ? WHERE msg_box = ?", new String[] {(23L | 0x80000000L | 0x800000L) +"", 6+""});
        db.execSQL("UPDATE mms SET msg_box = ? WHERE msg_box = ?", new String[] {(20L | 0x20000000L | 0x800000L) +"", 7+""});
        db.execSQL("UPDATE mms SET msg_box = ? WHERE msg_box = ?", new String[] {(20L | 0x80000000L | 0x800000L) +"", 8+""});
        db.execSQL("UPDATE mms SET msg_box = ? WHERE msg_box = ?", new String[] {(20L | 0x08000000L | 0x800000L) +"", 9+""});
        db.execSQL("UPDATE mms SET msg_box = ? WHERE msg_box = ?", new String[] {(20L | 0x10000000L | 0x800000L) +"", 10+""});

        // Thread Updates

        db.execSQL("ALTER TABLE thread ADD COLUMN snippet_type INTEGER;");

        db.execSQL("UPDATE thread SET snippet = substr(snippet, ?), " +
                   "snippet_type = ? WHERE snippet LIKE ?",
                   new String[] {(SYMMETRIC_ENCRYPT.length()+1)+"",
                                 0x80000000L+"",
                                 SYMMETRIC_ENCRYPT + "%"});

        db.execSQL("UPDATE thread SET snippet = substr(snippet, ?), " +
                   "snippet_type = ? WHERE snippet LIKE ?",
                   new String[] {(ASYMMETRIC_LOCAL_ENCRYPT.length()+1)+"",
                                  0x40000000L+"",
                                  ASYMMETRIC_LOCAL_ENCRYPT + "%"});

        db.execSQL("UPDATE thread SET snippet = substr(snippet, ?), " +
                   "snippet_type = ? WHERE snippet LIKE ?",
                   new String[] {(ASYMMETRIC_ENCRYPT.length()+1)+"",
                                 (0x800000L | 0x20000000L)+"",
                                 ASYMMETRIC_ENCRYPT + "%"});

        db.execSQL("UPDATE thread SET snippet = substr(snippet, ?), " +
                   "snippet_type = ? WHERE snippet LIKE ?",
                   new String[] {(KEY_EXCHANGE.length()+1)+"",
                       0x8000L+"",
                       KEY_EXCHANGE + "%"});

        db.execSQL("UPDATE thread SET snippet = substr(snippet, ?), " +
                   "snippet_type = ? WHERE snippet LIKE ?",
                   new String[] {(STALE_KEY_EXCHANGE.length()+1)+"",
                                 (0x8000L | 0x4000L)+"",
                                 STALE_KEY_EXCHANGE + "%"});

        db.execSQL("UPDATE thread SET snippet = substr(snippet, ?), " +
                   "snippet_type = ? WHERE snippet LIKE ?",
                   new String[] {(PROCESSED_KEY_EXCHANGE.length()+1)+"",
                                 (0x8000L | 0x2000L)+"",
                                 PROCESSED_KEY_EXCHANGE + "%"});
      }

      if (oldVersion < INTRODUCED_MMS_BODY_VERSION) {
        db.execSQL("ALTER TABLE mms ADD COLUMN body TEXT");
        db.execSQL("ALTER TABLE mms ADD COLUMN part_count INTEGER");
      }

      if (oldVersion < INTRODUCED_MMS_FROM_VERSION) {
        db.execSQL("ALTER TABLE mms ADD COLUMN address TEXT");

        Cursor cursor = db.query("mms_addresses", null, "type = ?", new String[] {0x89+""},
                                 null, null, null);

        while (cursor != null && cursor.moveToNext()) {
          long mmsId     = cursor.getLong(cursor.getColumnIndexOrThrow("mms_id"));
          String address = cursor.getString(cursor.getColumnIndexOrThrow("address"));

          if (!TextUtils.isEmpty(address)) {
            db.execSQL("UPDATE mms SET address = ? WHERE _id = ?", new String[]{address, mmsId+""});
          }
        }

        if (cursor != null)
          cursor.close();
      }

      if (oldVersion < INTRODUCED_TOFU_IDENTITY_VERSION) {
        db.execSQL("DROP TABLE identities");
        db.execSQL("CREATE TABLE identities (_id INTEGER PRIMARY KEY, recipient INTEGER UNIQUE, key TEXT, mac TEXT);");
      }

      if (oldVersion < INTRODUCED_PUSH_DATABASE_VERSION) {
        db.execSQL("CREATE TABLE push (_id INTEGER PRIMARY KEY, type INTEGER, source TEXT, destinations TEXT, body TEXT, TIMESTAMP INTEGER);");
        db.execSQL("ALTER TABLE part ADD COLUMN pending_push INTEGER;");
        db.execSQL("CREATE INDEX IF NOT EXISTS pending_push_index ON part (pending_push);");
      }

      if (oldVersion < INTRODUCED_GROUP_DATABASE_VERSION) {
        db.execSQL("CREATE TABLE groups (_id INTEGER PRIMARY KEY, group_id TEXT, title TEXT, members TEXT, avatar BLOB, avatar_id INTEGER, avatar_key BLOB, avatar_content_type TEXT, avatar_relay TEXT, timestamp INTEGER, active INTEGER DEFAULT 1);");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS group_id_index ON groups (GROUP_ID);");
        db.execSQL("ALTER TABLE push ADD COLUMN device_id INTEGER DEFAULT 1;");
        db.execSQL("ALTER TABLE sms ADD COLUMN address_device_id INTEGER DEFAULT 1;");
        db.execSQL("ALTER TABLE mms ADD COLUMN address_device_id INTEGER DEFAULT 1;");
      }

      if (oldVersion < INTRODUCED_PUSH_FIX_VERSION) {
        db.execSQL("CREATE TEMPORARY table push_backup (_id INTEGER PRIMARY KEY, type INTEGER, source, TEXT, destinations TEXT, body TEXT, timestamp INTEGER, device_id INTEGER DEFAULT 1);");
        db.execSQL("INSERT INTO push_backup(_id, type, source, body, timestamp, device_id) SELECT _id, type, source, body, timestamp, device_id FROM push;");
        db.execSQL("DROP TABLE push");
        db.execSQL("CREATE TABLE push (_id INTEGER PRIMARY KEY, type INTEGER, source TEXT, body TEXT, timestamp INTEGER, device_id INTEGER DEFAULT 1);");
        db.execSQL("INSERT INTO push (_id, type, source, body, timestamp, device_id) SELECT _id, type, source, body, timestamp, device_id FROM push_backup;");
        db.execSQL("DROP TABLE push_backup;");
      }

      if (oldVersion < INTRODUCED_DELIVERY_RECEIPTS) {
        db.execSQL("ALTER TABLE sms ADD COLUMN delivery_receipt_count INTEGER DEFAULT 0;");
        db.execSQL("ALTER TABLE mms ADD COLUMN delivery_receipt_count INTEGER DEFAULT 0;");
        db.execSQL("CREATE INDEX IF NOT EXISTS sms_date_sent_index ON sms (date_sent);");
        db.execSQL("CREATE INDEX IF NOT EXISTS mms_date_sent_index ON mms (date);");
      }

      if (oldVersion < INTRODUCED_PART_DATA_SIZE_VERSION) {
        db.execSQL("ALTER TABLE part ADD COLUMN data_size INTEGER DEFAULT 0;");
      }

      if (oldVersion < INTRODUCED_THUMBNAILS_VERSION) {
        db.execSQL("ALTER TABLE part ADD COLUMN thumbnail TEXT;");
        db.execSQL("ALTER TABLE part ADD COLUMN aspect_ratio REAL;");
      }

      if (oldVersion < INTRODUCED_IDENTITY_COLUMN_VERSION) {
        db.execSQL("ALTER TABLE sms ADD COLUMN mismatched_identities TEXT");
        db.execSQL("ALTER TABLE mms ADD COLUMN mismatched_identities TEXT");
        db.execSQL("ALTER TABLE mms ADD COLUMN network_failures TEXT");
      }

      if (oldVersion < INTRODUCED_UNIQUE_PART_IDS_VERSION) {
        db.execSQL("ALTER TABLE part ADD COLUMN unique_id INTEGER NOT NULL DEFAULT 0");
      }

      if (oldVersion < INTRODUCED_RECIPIENT_PREFS_DB) {
        db.execSQL("CREATE TABLE recipient_preferences " +
                   "(_id INTEGER PRIMARY KEY, recipient_ids TEXT UNIQUE, block INTEGER DEFAULT 0, " +
                   "notification TEXT DEFAULT NULL, vibrate INTEGER DEFAULT 0, mute_until INTEGER DEFAULT 0)");
      }

      if (oldVersion < INTRODUCED_ENVELOPE_CONTENT_VERSION) {
        db.execSQL("ALTER TABLE push ADD COLUMN content TEXT");
      }

      if (oldVersion < INTRODUCED_COLOR_PREFERENCE_VERSION) {
        db.execSQL("ALTER TABLE recipient_preferences ADD COLUMN color TEXT DEFAULT NULL");
      }

      if (oldVersion < INTRODUCED_DB_OPTIMIZATIONS_VERSION) {
        db.execSQL("UPDATE mms SET date_received = (date_received * 1000), date = (date * 1000);");
        db.execSQL("CREATE INDEX IF NOT EXISTS sms_thread_date_index ON sms (thread_id, date);");
        db.execSQL("CREATE INDEX IF NOT EXISTS mms_thread_date_index ON mms (thread_id, date_received);");
      }

      if (oldVersion < INTRODUCED_INVITE_REMINDERS_VERSION) {
        db.execSQL("ALTER TABLE recipient_preferences ADD COLUMN seen_invite_reminder INTEGER DEFAULT 0");
      }

      if (oldVersion < INTRODUCED_CONVERSATION_LIST_THUMBNAILS_VERSION) {
        db.execSQL("ALTER TABLE thread ADD COLUMN snippet_uri TEXT DEFAULT NULL");
      }

      if (oldVersion < INTRODUCED_ARCHIVE_VERSION) {
        db.execSQL("ALTER TABLE thread ADD COLUMN archived INTEGER DEFAULT 0");
        db.execSQL("CREATE INDEX IF NOT EXISTS archived_index ON thread (archived)");
      }

      if (oldVersion < INTRODUCED_CONVERSATION_LIST_STATUS_VERSION) {
        db.execSQL("ALTER TABLE thread ADD COLUMN status INTEGER DEFAULT -1");
        db.execSQL("ALTER TABLE thread ADD COLUMN delivery_receipt_count INTEGER DEFAULT 0");
      }

      if (oldVersion < MIGRATED_CONVERSATION_LIST_STATUS_VERSION) {
        Cursor threadCursor = db.query("thread", new String[] {"_id"}, null, null, null, null, null);

        while (threadCursor != null && threadCursor.moveToNext()) {
          long threadId = threadCursor.getLong(threadCursor.getColumnIndexOrThrow("_id"));

          Cursor cursor = db.rawQuery("SELECT DISTINCT date AS date_received, status, " +
                                      "delivery_receipt_count FROM sms WHERE (thread_id = ?1) " +
                                      "UNION ALL SELECT DISTINCT date_received, -1 AS status, " +
                                      "delivery_receipt_count FROM mms WHERE (thread_id = ?1) " +
                                      "ORDER BY date_received DESC LIMIT 1", new String[]{threadId + ""});

          if (cursor != null && cursor.moveToNext()) {
            int status       = cursor.getInt(cursor.getColumnIndexOrThrow("status"));
            int receiptCount = cursor.getInt(cursor.getColumnIndexOrThrow("delivery_receipt_count"));

            db.execSQL("UPDATE thread SET status = ?, delivery_receipt_count = ? WHERE _id = ?",
                       new String[]{status + "", receiptCount + "", threadId + ""});
          }
        }
      }

      if (oldVersion < INTRODUCED_SUBSCRIPTION_ID_VERSION) {
        db.execSQL("ALTER TABLE recipient_preferences ADD COLUMN default_subscription_id INTEGER DEFAULT -1");
        db.execSQL("ALTER TABLE sms ADD COLUMN subscription_id INTEGER DEFAULT -1");
        db.execSQL("ALTER TABLE mms ADD COLUMN subscription_id INTEGER DEFAULT -1");
      }

      if (oldVersion < INTRODUCED_EXPIRE_MESSAGES_VERSION) {
        db.execSQL("ALTER TABLE recipient_preferences ADD COLUMN expire_messages INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE sms ADD COLUMN expires_in INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE mms ADD COLUMN expires_in INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE sms ADD COLUMN expire_started INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE mms ADD COLUMN expire_started INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE thread ADD COLUMN expires_in INTEGER DEFAULT 0");
      }

      if (oldVersion < INTRODUCED_LAST_SEEN) {
        db.execSQL("ALTER TABLE thread ADD COLUMN last_seen INTEGER DEFAULT 0");
      }

      if (oldVersion < INTRODUCED_DIGEST) {
        db.execSQL("ALTER TABLE part ADD COLUMN digest BLOB");
        db.execSQL("ALTER TABLE groups ADD COLUMN avatar_digest BLOB");
      }

      if (oldVersion < INTRODUCED_NOTIFIED) {
        db.execSQL("ALTER TABLE sms ADD COLUMN notified INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE mms ADD COLUMN notified INTEGER DEFAULT 0");

        db.execSQL("DROP INDEX sms_read_and_thread_id_index");
        db.execSQL("CREATE INDEX IF NOT EXISTS sms_read_and_notified_and_thread_id_index ON sms(read,notified,thread_id)");

        db.execSQL("DROP INDEX mms_read_and_thread_id_index");
        db.execSQL("CREATE INDEX IF NOT EXISTS mms_read_and_notified_and_thread_id_index ON mms(read,notified,thread_id)");
      }

      if (oldVersion < INTRODUCED_DOCUMENTS) {
        db.execSQL("ALTER TABLE part ADD COLUMN file_name TEXT");
      }

      if (oldVersion < INTRODUCED_FAST_PREFLIGHT) {
        db.execSQL("ALTER TABLE part ADD COLUMN fast_preflight_id TEXT");
      }

      if (oldVersion < INTRODUCED_VOICE_NOTES) {
        db.execSQL("ALTER TABLE part ADD COLUMN voice_note INTEGER DEFAULT 0");
      }

      if (oldVersion < INTRODUCED_IDENTITY_TIMESTAMP) {
        db.execSQL("ALTER TABLE identities ADD COLUMN timestamp INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE identities ADD COLUMN first_use INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE identities ADD COLUMN nonblocking_approval INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE identities ADD COLUMN verified INTEGER DEFAULT 0");

        db.execSQL("DROP INDEX archived_index");
        db.execSQL("CREATE INDEX IF NOT EXISTS archived_count_index ON thread (archived, message_count)");
      }

      if (oldVersion < SANIFY_ATTACHMENT_DOWNLOAD) {
        db.execSQL("UPDATE part SET pending_push = '2' WHERE pending_push = '1'");
      }

      if (oldVersion < NO_MORE_CANONICAL_ADDRESS_DATABASE) {
        DatabaseHelper canonicalAddressDatabaseHelper = new DatabaseHelper(context, "canonical_address.db", null, 1);
        SQLiteDatabase canonicalAddressDatabase       = canonicalAddressDatabaseHelper.getReadableDatabase();
        NumberMigrator numberMigrator                 = new NumberMigrator(TextSecurePreferences.getLocalNumber(context));

        // Migrate Thread Database
        Cursor cursor = db.query("thread", new String[] {"_id", "recipient_ids"}, null, null, null, null, null);

        while (cursor != null && cursor.moveToNext()) {
          long     threadId         = cursor.getLong(0);
          String   recipientIdsList = cursor.getString(1);
          String[] recipientIds     = recipientIdsList.split(" ");
          String[] numbers          = new String[recipientIds.length];

          for (int i=0;i<recipientIds.length;i++) {
            Cursor resolved = canonicalAddressDatabase.query("canonical_addresses", new String[] {"address"}, "_id = ?", new String[] {recipientIds[i]}, null, null, null);

            if (resolved != null && resolved.moveToFirst()) {
              String address = resolved.getString(0);
              numbers[i] = numberMigrator.migrate(address);
            } else {
              throw new AssertionError("Unable to resolve: " + recipientIds[i]);
            }

            if (resolved != null) resolved.close();
          }

          ContentValues values = new ContentValues(1);
          values.put("recipient_ids", Util.join(numbers, " "));
          db.update("thread", values, "_id = ?", new String[] {String.valueOf(threadId)});
        }

        if (cursor != null) cursor.close();

        // Migrate Identity database
        db.execSQL("CREATE TABLE identities_migrated (_id INTEGER PRIMARY KEY, address TEXT UNIQUE, key TEXT, first_use INTEGER DEFAULT 0, timestamp INTEGER DEFAULT 0, verified INTEGER DEFAULT 0, nonblocking_approval INTEGER DEFAULT 0);");

        cursor = db.query("identities", new String[] {"_id, recipient, key, first_use, timestamp, verified, nonblocking_approval"}, null, null, null, null, null);

        while (cursor != null && cursor.moveToNext()) {
          long   id                  = cursor.getLong(0);
          long   recipientId         = cursor.getLong(1);
          String key                 = cursor.getString(2);
          int    firstUse            = cursor.getInt(3);
          long   timestamp           = cursor.getLong(4);
          int    verified            = cursor.getInt(5);
          int    nonblockingApproval = cursor.getInt(6);

          ContentValues values      = new ContentValues(6);

          Cursor resolved = canonicalAddressDatabase.query("canonical_addresses", new String[] {"address"}, "_id = ?", new String[] {String.valueOf(recipientId)}, null, null, null);

          if (resolved != null && resolved.moveToFirst()) {
            String address = resolved.getString(0);
            values.put("address", numberMigrator.migrate(address));
            values.put("key", key);
            values.put("first_use", firstUse);
            values.put("timestamp", timestamp);
            values.put("verified", verified);
            values.put("nonblocking_approval", nonblockingApproval);
          } else {
            throw new AssertionError("Unable to resolve: " + recipientId);
          }

          if (resolved != null) resolved.close();

          db.insert("identities_migrated", null, values);
        }

        if (cursor != null) cursor.close();

        db.execSQL("DROP TABLE identities");
        db.execSQL("ALTER TABLE identities_migrated RENAME TO identities");

        // Migrate recipient preferences database
        cursor = db.query("recipient_preferences", new String[] {"_id", "recipient_ids"}, null, null, null, null, null);

        while (cursor != null && cursor.moveToNext()) {
          long     id               = cursor.getLong(0);
          String   recipientIdsList = cursor.getString(1);
          String[] recipientIds     = recipientIdsList.split(" ");
          String[] addresses        = new String[recipientIds.length];

          for (int i=0;i<recipientIds.length;i++) {
            Cursor resolved = canonicalAddressDatabase.query("canonical_addresses", new String[] {"address"}, "_id = ?", new String[] {recipientIds[i]}, null, null, null);

            if (resolved != null && resolved.moveToFirst()) {
              String address = resolved.getString(0);
              addresses[i] = numberMigrator.migrate(address);
            } else {
              throw new AssertionError("Unable to resolve: " + recipientIds[i]);
            }

            if (resolved != null) resolved.close();
          }

          ContentValues values = new ContentValues(1);
          values.put("recipient_ids", Util.join(addresses, " "));
          db.update("thread", values, "_id = ?", new String[] {String.valueOf(id)});
        }

        if (cursor != null) cursor.close();

        // Migrate SMS database
        cursor = db.query("sms", new String[] {"_id", "address"}, null, null, null, null, null);

        while (cursor != null && cursor.moveToNext()) {
          long   id      = cursor.getLong(0);
          String address = cursor.getString(1);

          if (!TextUtils.isEmpty(address)) {
            ContentValues values = new ContentValues(1);
            values.put("address", numberMigrator.migrate(address));
            db.update("sms", values, "_id = ?", new String[] {String.valueOf(id)});
          }
        }

        if (cursor != null) cursor.close();

        // Migrate MMS database
        cursor = db.query("mms", new String[] {"_id", "address"}, null, null, null, null, null);

        while (cursor != null && cursor.moveToNext()) {
          long   id      = cursor.getLong(0);
          String address = cursor.getString(1);

          if (!TextUtils.isEmpty(address)) {
            ContentValues values = new ContentValues(1);
            values.put("address", numberMigrator.migrate(address));
            db.update("mms", values, "_id = ?", new String[] {String.valueOf(id)});
          }
        }

        if (cursor != null) cursor.close();

        // Migrate MmsAddressDatabase
        cursor = db.query("mms_addresses", new String[] {"_id", "address"}, null, null, null, null, null);

        while (cursor != null && cursor.moveToNext()) {
          long   id      = cursor.getLong(0);
          String address = cursor.getString(1);

          if (!TextUtils.isEmpty(address) && !"insert-address-token".equals(address)) {
            ContentValues values = new ContentValues(1);
            values.put("address", numberMigrator.migrate(address));
            db.update("mms_addresses", values, "_id = ?", new String[] {String.valueOf(id)});
          }
        }

        if (cursor != null) cursor.close();

        // Migrate SMS mismatched identities
        cursor = db.query("sms", new String[] {"_id", "mismatched_identities"}, "mismatched_identities IS NOT NULL", null, null, null, null);

        while (cursor != null && cursor.moveToNext()) {
          long   id       = cursor.getLong(0);
          String document = cursor.getString(1);

          if (!TextUtils.isEmpty(document)) {
            try {
              PreCanonicalAddressIdentityMismatchList            oldDocumentList = JsonUtils.fromJson(document, PreCanonicalAddressIdentityMismatchList.class);
              List<PostCanonicalAddressIdentityMismatchDocument> newDocumentList = new LinkedList<>();

              for (PreCanonicalAddressIdentityMismatchDocument oldDocument : oldDocumentList.list) {
                Cursor resolved = canonicalAddressDatabase.query("canonical_addresses", new String[] {"address"}, "_id = ?", new String[] {String.valueOf(oldDocument.recipientId)}, null, null, null);

                if (resolved != null && resolved.moveToFirst()) {
                  String address = resolved.getString(0);
                  newDocumentList.add(new PostCanonicalAddressIdentityMismatchDocument(numberMigrator.migrate(address), oldDocument.identityKey));
                } else {
                  throw new AssertionError("Unable to resolve: " + oldDocument.recipientId);
                }

                if (resolved != null) resolved.close();
              }

              ContentValues values = new ContentValues(1);
              values.put("mismatched_identities", JsonUtils.toJson(new PostCanonicalAddressIdentityMismatchList(newDocumentList)));
              db.update("sms", values, "_id = ?", new String[] {String.valueOf(id)});
            } catch (IOException e) {
              Log.w(TAG, e);
            }
          }
        }

        if (cursor != null) cursor.close();

        // Migrate MMS mismatched identities
        cursor = db.query("mms", new String[] {"_id", "mismatched_identities"}, "mismatched_identities IS NOT NULL", null, null, null, null);

        while (cursor != null && cursor.moveToNext()) {
          long   id       = cursor.getLong(0);
          String document = cursor.getString(1);

          if (!TextUtils.isEmpty(document)) {
            try {
              PreCanonicalAddressIdentityMismatchList            oldDocumentList = JsonUtils.fromJson(document, PreCanonicalAddressIdentityMismatchList.class);
              List<PostCanonicalAddressIdentityMismatchDocument> newDocumentList = new LinkedList<>();

              for (PreCanonicalAddressIdentityMismatchDocument oldDocument : oldDocumentList.list) {
                Cursor resolved = canonicalAddressDatabase.query("canonical_addresses", new String[] {"address"}, "_id = ?", new String[] {String.valueOf(oldDocument.recipientId)}, null, null, null);

                if (resolved != null && resolved.moveToFirst()) {
                  String address = resolved.getString(0);
                  newDocumentList.add(new PostCanonicalAddressIdentityMismatchDocument(numberMigrator.migrate(address), oldDocument.identityKey));
                } else {
                  throw new AssertionError("Unable to resolve: " + oldDocument.recipientId);
                }

                if (resolved != null) resolved.close();
              }

              ContentValues values = new ContentValues(1);
              values.put("mismatched_identities", JsonUtils.toJson(new PostCanonicalAddressIdentityMismatchList(newDocumentList)));
              db.update("mms", values, "_id = ?", new String[] {String.valueOf(id)});
            } catch (IOException e) {
              Log.w(TAG, e);
            }
          }
        }

        if (cursor != null) cursor.close();

        // Migrate MMS network failures
        cursor = db.query("mms", new String[] {"_id", "network_failures"}, "network_failures IS NOT NULL", null, null, null, null);

        while (cursor != null && cursor.moveToNext()) {
          long   id       = cursor.getLong(0);
          String document = cursor.getString(1);

          if (!TextUtils.isEmpty(document)) {
            try {
              PreCanonicalAddressNetworkFailureList            oldDocumentList = JsonUtils.fromJson(document, PreCanonicalAddressNetworkFailureList.class);
              List<PostCanonicalAddressNetworkFailureDocument> newDocumentList = new LinkedList<>();

              for (PreCanonicalAddressNetworkFailureDocument oldDocument : oldDocumentList.list) {
                Cursor resolved = canonicalAddressDatabase.query("canonical_addresses", new String[] {"address"}, "_id = ?", new String[] {String.valueOf(oldDocument.recipientId)}, null, null, null);

                if (resolved != null && resolved.moveToFirst()) {
                  String address = resolved.getString(0);
                  newDocumentList.add(new PostCanonicalAddressNetworkFailureDocument(numberMigrator.migrate(address)));
                } else {
                  throw new AssertionError("Unable to resolve: " + oldDocument.recipientId);
                }

                if (resolved != null) resolved.close();
              }

              ContentValues values = new ContentValues(1);
              values.put("network_failures", JsonUtils.toJson(new PostCanonicalAddressNetworkFailureList(newDocumentList)));
              db.update("mms", values, "_id = ?", new String[] {String.valueOf(id)});
            } catch (IOException e) {
              Log.w(TAG, e);
            }
          }
        }

        // Migrate sessions
        File sessionsDirectory = new File(context.getFilesDir(), "sessions-v2");

        if (sessionsDirectory.exists() && sessionsDirectory.isDirectory()) {
          File[] sessions = sessionsDirectory.listFiles();

          for (File session : sessions) {
            try {
              String[] sessionParts = session.getName().split("[.]");
              long     recipientId  = Long.parseLong(sessionParts[0]);

              int deviceId;

              if (sessionParts.length > 1) deviceId = Integer.parseInt(sessionParts[1]);
              else                         deviceId = 1;

              Cursor resolved = canonicalAddressDatabase.query("canonical_addresses", new String[] {"address"}, "_id = ?", new String[] {String.valueOf(recipientId)}, null, null, null);

              if (resolved != null && resolved.moveToNext()) {
                String address     = resolved.getString(0);
                File   destination = new File(session.getParentFile(), address + (deviceId != 1 ? "." + deviceId : ""));

                if (!session.renameTo(destination)) {
                  Log.w(TAG, "Session rename failed: " + destination);
                }
              }

              if (resolved != null) resolved.close();
            } catch (NumberFormatException e) {
              Log.w(TAG, e);
            }
          }
        }

      }

      db.setTransactionSuccessful();
      db.endTransaction();
    }

    private void executeStatements(SQLiteDatabase db, String[] statements) {
      for (String statement : statements)
        db.execSQL(statement);
    }

  }

  private static class PreCanonicalAddressIdentityMismatchList {
    @JsonProperty(value = "m")
    private List<PreCanonicalAddressIdentityMismatchDocument> list;
  }

  private static class PostCanonicalAddressIdentityMismatchList {
    @JsonProperty(value = "m")
    private List<PostCanonicalAddressIdentityMismatchDocument> list;

    public PostCanonicalAddressIdentityMismatchList(List<PostCanonicalAddressIdentityMismatchDocument> list) {
      this.list = list;
    }
  }

  private static class PreCanonicalAddressIdentityMismatchDocument {
    @JsonProperty(value = "r")
    private long recipientId;

    @JsonProperty(value = "k")
    private String identityKey;
  }

  private static class PostCanonicalAddressIdentityMismatchDocument {
    @JsonProperty(value = "a")
    private String address;

    @JsonProperty(value = "k")
    private String identityKey;

    public PostCanonicalAddressIdentityMismatchDocument() {}

    public PostCanonicalAddressIdentityMismatchDocument(String address, String identityKey) {
      this.address     = address;
      this.identityKey = identityKey;
    }
  }

  private static class PreCanonicalAddressNetworkFailureList {
    @JsonProperty(value = "l")
    private List<PreCanonicalAddressNetworkFailureDocument> list;
  }

  private static class PostCanonicalAddressNetworkFailureList {
    @JsonProperty(value = "l")
    private List<PostCanonicalAddressNetworkFailureDocument> list;

    public PostCanonicalAddressNetworkFailureList(List<PostCanonicalAddressNetworkFailureDocument> list) {
      this.list = list;
    }
  }

  private static class PreCanonicalAddressNetworkFailureDocument {
    @JsonProperty(value = "r")
    private long recipientId;
  }

  private static class PostCanonicalAddressNetworkFailureDocument {
    @JsonProperty(value = "a")
    private String address;

    public PostCanonicalAddressNetworkFailureDocument() {}

    public PostCanonicalAddressNetworkFailureDocument(String address) {
      this.address = address;
    }
  }

  private static class NumberMigrator {

    private static final String TAG = NumberMigrator.class.getSimpleName();

    private static final Set<String> SHORT_COUNTRIES = new HashSet<String>() {{
      add("NU");
      add("TK");
      add("NC");
      add("AC");
    }};

    private final Phonenumber.PhoneNumber localNumber;
    private final String                  localNumberString;
    private final String                  localCountryCode;

    private final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();

    public NumberMigrator(String localNumber) {
      try {
        this.localNumberString = localNumber;
        this.localNumber       = phoneNumberUtil.parse(localNumber, null);
        this.localCountryCode  = phoneNumberUtil.getRegionCodeForNumber(this.localNumber);
      } catch (NumberParseException e) {
        throw new AssertionError(e);
      }
    }

    public String migrate(@Nullable String number) {
      if (number == null)                                                return "Unknown";
      if (number.startsWith("__textsecure_group__!"))                    return number;
      if (android.util.Patterns.EMAIL_ADDRESS.matcher(number).matches()) return number;

      String bareNumber = number.replaceAll("[^0-9+]", "");

      if (bareNumber.length() == 0) {
        if (TextUtils.isEmpty(number.trim())) return "Unknown";
        else                                  return number.trim();
      }

      // libphonenumber doesn't seem to be correct for Germany and Finland
      if (bareNumber.length() <= 6 && ("DE".equals(localCountryCode) || "FI".equals(localCountryCode) || "SK".equals(localCountryCode))) {
        return bareNumber;
      }

      // libphonenumber seems incorrect for Russia and a few other countries with 4 digit short codes.
      if (bareNumber.length() <= 4 && !SHORT_COUNTRIES.contains(localCountryCode)) {
        return bareNumber;
      }

      try {
        Phonenumber.PhoneNumber parsedNumber = phoneNumberUtil.parse(bareNumber, localCountryCode);

        if (ShortNumberInfo.getInstance().isPossibleShortNumberForRegion(parsedNumber, localCountryCode)) {
          return bareNumber;
        }

        return phoneNumberUtil.format(parsedNumber, PhoneNumberUtil.PhoneNumberFormat.E164);
      } catch (NumberParseException e) {
        Log.w(TAG, e);
        if (bareNumber.charAt(0) == '+')
          return bareNumber;

        String localNumberImprecise = localNumberString;

        if (localNumberImprecise.charAt(0) == '+')
          localNumberImprecise = localNumberImprecise.substring(1);

        if (localNumberImprecise.length() == number.length() || number.length() > localNumberImprecise.length())
          return "+" + number;

        int difference = localNumberImprecise.length() - number.length();

        return "+" + localNumberImprecise.substring(0, difference) + number;
      }
    }



  }


}
