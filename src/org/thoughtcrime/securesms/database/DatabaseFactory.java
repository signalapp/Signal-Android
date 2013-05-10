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
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import org.thoughtcrime.securesms.DatabaseUpgradeActivity;
import org.thoughtcrime.securesms.crypto.DecryptingPartInputStream;
import org.thoughtcrime.securesms.crypto.MasterCipher;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.util.InvalidMessageException;
import org.thoughtcrime.securesms.util.Util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import ws.com.google.android.mms.ContentType;

public class DatabaseFactory {

  private static final int INTRODUCED_IDENTITIES_VERSION  = 2;
  private static final int INTRODUCED_INDEXES_VERSION     = 3;
  private static final int INTRODUCED_DATE_SENT_VERSION   = 4;
  private static final int INTRODUCED_DRAFTS_VERSION      = 5;
  private static final int INTRODUCED_NEW_TYPES_VERSION   = 6;
  private static final int INTRODUCED_MMS_BODY_VERSION    = 7;
  private static final int INTRODUCED_MMS_FROM_VERSION    = 8;
  private static final int DATABASE_VERSION               = 8;

  private static final String DATABASE_NAME    = "messages.db";
  private static final Object lock             = new Object();

  private static DatabaseFactory instance;
  private static EncryptingPartDatabase encryptingPartInstance;

  private final DatabaseHelper databaseHelper;

  private final SmsDatabase sms;
  private final EncryptingSmsDatabase encryptingSms;
  private final MmsDatabase mms;
  private final PartDatabase part;
  private final ThreadDatabase thread;
  private final CanonicalAddressDatabase address;
  private final MmsAddressDatabase mmsAddress;
  private final MmsSmsDatabase mmsSmsDatabase;
  private final IdentityDatabase identityDatabase;
  private final DraftDatabase draftDatabase;

  public static DatabaseFactory getInstance(Context context) {
    synchronized (lock) {
      if (instance == null)
        instance = new DatabaseFactory(context);

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

  public static CanonicalAddressDatabase getAddressDatabase(Context context) {
    return getInstance(context).address;
  }

  public static EncryptingSmsDatabase getEncryptingSmsDatabase(Context context) {
    return getInstance(context).encryptingSms;
  }

  public static PartDatabase getPartDatabase(Context context) {
    return getInstance(context).part;
  }

  public static EncryptingPartDatabase getEncryptingPartDatabase(Context context, MasterSecret masterSecret) {
    synchronized (lock)  {
      if (encryptingPartInstance == null) {
        DatabaseFactory factory = getInstance(context);
        encryptingPartInstance  = new EncryptingPartDatabase(context, factory.databaseHelper, masterSecret);
      }

      return encryptingPartInstance;
    }
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

  private DatabaseFactory(Context context) {
    this.databaseHelper   = new DatabaseHelper(context, DATABASE_NAME, null, DATABASE_VERSION);
    this.sms              = new SmsDatabase(context, databaseHelper);
    this.encryptingSms    = new EncryptingSmsDatabase(context, databaseHelper);
    this.mms              = new MmsDatabase(context, databaseHelper);
    this.part             = new PartDatabase(context, databaseHelper);
    this.thread           = new ThreadDatabase(context, databaseHelper);
    this.address          = CanonicalAddressDatabase.getInstance(context);
    this.mmsAddress       = new MmsAddressDatabase(context, databaseHelper);
    this.mmsSmsDatabase   = new MmsSmsDatabase(context, databaseHelper);
    this.identityDatabase = new IdentityDatabase(context, databaseHelper);
    this.draftDatabase    = new DraftDatabase(context, databaseHelper);
  }

  public void close() {
    databaseHelper.close();
    address.close();
    instance = null;
  }

  public void onApplicationLevelUpgrade(MasterSecret masterSecret, int fromVersion,
                                        DatabaseUpgradeActivity.DatabaseUpgradeListener listener)
  {
    if (fromVersion < DatabaseUpgradeActivity.NO_MORE_KEY_EXCHANGE_PREFIX_VERSION) {
      String KEY_EXCHANGE             = "?TextSecureKeyExchange";
      String PROCESSED_KEY_EXCHANGE   = "?TextSecureKeyExchangd";
      String STALE_KEY_EXCHANGE       = "?TextSecureKeyExchangs";

      MasterCipher masterCipher = new MasterCipher(masterSecret);
      int count                 = 0;
      SQLiteDatabase db         = databaseHelper.getWritableDatabase();
      Cursor smsCursor          = db.query("sms",
                                           new String[] {"_id", "type", "body"},
                                           "type & " + 0x80000000 + " != 0",
                                           null, null, null, null);

      Cursor threadCursor       = db.query("thread",
                                           new String[] {"_id", "snippet_type", "snippet"},
                                           "snippet_type & " + 0x80000000 + " != 0",
                                           null, null, null, null);

      if (smsCursor != null)
        count = smsCursor.getCount();

      if (threadCursor != null)
        count += threadCursor.getCount();

      db.beginTransaction();

      while (smsCursor != null && smsCursor.moveToNext()) {
        listener.setProgress(smsCursor.getPosition(), count);

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
            type |= 0x2000;

            db.execSQL("UPDATE sms SET body = ?, type = ? WHERE _id = ?",
                       new String[] {body, type+"", id+""});
          } else if (body.startsWith(STALE_KEY_EXCHANGE)) {
            body  = body.substring(STALE_KEY_EXCHANGE.length());
            body  = masterCipher.encryptBody(body);
            type |= 0x4000;

            db.execSQL("UPDATE sms SET body = ?, type = ? WHERE _id = ?",
                       new String[] {body, type+"", id+""});
          }
        } catch (InvalidMessageException e) {
          Log.w("DatabaseFactory", e);
        }
      }

      while (threadCursor != null && threadCursor.moveToNext()) {
        listener.setProgress(smsCursor.getCount() + threadCursor.getPosition(), count);

        try {
          String snippet   = threadCursor.getString(threadCursor.getColumnIndexOrThrow("snippet"));
          long snippetType = threadCursor.getLong(threadCursor.getColumnIndexOrThrow("snippet_type"));
          long id          = threadCursor.getLong(threadCursor.getColumnIndexOrThrow("_id"));

          if (!Util.isEmpty(snippet)) {
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
            snippetType |= 0x2000;

            db.execSQL("UPDATE thread SET snippet = ?, snippet_type = ? WHERE _id = ?",
                       new String[] {snippet, snippetType+"", id+""});
          } else if (snippet.startsWith(STALE_KEY_EXCHANGE)) {
            snippet      = snippet.substring(STALE_KEY_EXCHANGE.length());
            snippet      = masterCipher.encryptBody(snippet);
            snippetType |= 0x4000;

            db.execSQL("UPDATE thread SET snippet = ?, snippet_type = ? WHERE _id = ?",
                       new String[] {snippet, snippetType+"", id+""});
          }
        } catch (InvalidMessageException e) {
          Log.w("DatabaseFactory", e);
        }
      }

      db.setTransactionSuccessful();
      db.endTransaction();

      smsCursor.close();
      threadCursor.close();
    }

    if (fromVersion < DatabaseUpgradeActivity.MMS_BODY_VERSION) {
      Log.w("DatabaseFactory", "Update MMS bodies...");
      MasterCipher masterCipher = new MasterCipher(masterSecret);
      SQLiteDatabase db         = databaseHelper.getWritableDatabase();

      db.beginTransaction();

      Cursor mmsCursor = db.query("mms", new String[] {"_id"},
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

          if (ContentType.isTextType(contentType)) {
            try {
              long partId         = partCursor.getLong(partCursor.getColumnIndexOrThrow("_id"));
              String dataLocation = partCursor.getString(partCursor.getColumnIndexOrThrow("_data"));
              boolean encrypted   = partCursor.getInt(partCursor.getColumnIndexOrThrow("encrypted")) == 1;
              File dataFile       = new File(dataLocation);

              FileInputStream fin;

              if (encrypted) fin = new DecryptingPartInputStream(dataFile, masterSecret);
              else           fin = new FileInputStream(dataFile);

              body = (body == null) ? Util.readFully(fin) : body + " " + Util.readFully(fin);

              dataFile.delete();
              db.delete("part", "_id = ?", new String[] {partId+""});
            } catch (IOException e) {
              Log.w("DatabaseFactory", e);
            }
          } else if (ContentType.isAudioType(contentType) ||
                     ContentType.isImageType(contentType) ||
                     ContentType.isVideoType(contentType))
          {
            partCount++;
          }
        }

        if (!Util.isEmpty(body)) {
          body = masterCipher.encryptBody(body);
          db.execSQL("UPDATE mms SET body = ?, part_count = ? WHERE _id = ?",
                     new String[] {body, partCount+"", mmsId+""});
        } else {
          db.execSQL("UPDATE mms SET part_count = ? WHERE _id = ?",
                     new String[] {partCount+"", mmsId+""});
        }

        Log.w("DatabaseFactory", "Updated body: " + body + " and part_count: " + partCount);
      }

      db.setTransactionSuccessful();
      db.endTransaction();
    }
  }

  private static class DatabaseHelper extends SQLiteOpenHelper {

    public DatabaseHelper(Context context, String name, CursorFactory factory, int version) {
      super(context, name, factory, version);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
      db.execSQL(SmsDatabase.CREATE_TABLE);
      db.execSQL(MmsDatabase.CREATE_TABLE);
      db.execSQL(PartDatabase.CREATE_TABLE);
      db.execSQL(ThreadDatabase.CREATE_TABLE);
      db.execSQL(MmsAddressDatabase.CREATE_TABLE);
      db.execSQL(IdentityDatabase.CREATE_TABLE);
      db.execSQL(DraftDatabase.CREATE_TABLE);

      executeStatements(db, SmsDatabase.CREATE_INDEXS);
      executeStatements(db, MmsDatabase.CREATE_INDEXS);
      executeStatements(db, PartDatabase.CREATE_INDEXS);
      executeStatements(db, ThreadDatabase.CREATE_INDEXS);
      executeStatements(db, MmsAddressDatabase.CREATE_INDEXS);
      executeStatements(db, DraftDatabase.CREATE_INDEXS);
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
        db.execSQL("UPDATE sms SET type = ? WHERE type = ?", new String[] {(20L | 0x800000L | 0x20000000L)+"", 47L+""});
        db.execSQL("UPDATE sms SET type = ? WHERE type = ?", new String[] {(20L | 0x800000L | 0x08000000L)+"", 48L+""});

        Cursor cursor = db.query("sms", null,"body LIKE ?", new String[] {SYMMETRIC_ENCRYPT + "%"},
                                 null, null, null);

        updateSmsBodyAndType(db, cursor, SYMMETRIC_ENCRYPT, 0x80000000L);

        if (cursor != null)
          cursor.close();

        cursor = db.query("sms", null,"body LIKE ?", new String[] {ASYMMETRIC_LOCAL_ENCRYPT + "%"},
                                 null, null, null);

        updateSmsBodyAndType(db, cursor, ASYMMETRIC_LOCAL_ENCRYPT, 0x40000000L);

        if (cursor != null)
          cursor.close();

        cursor = db.query("sms", null,"body LIKE ?", new String[] {ASYMMETRIC_ENCRYPT + "%"},
                          null, null, null);

        updateSmsBodyAndType(db, cursor, ASYMMETRIC_ENCRYPT, 0L);

        if (cursor != null)
          cursor.close();

        cursor = db.query("sms", null,"body LIKE ?", new String[] {KEY_EXCHANGE + "%"},
                          null, null, null);

        updateSmsBodyAndType(db, cursor, KEY_EXCHANGE, 0x8000L);

        if (cursor != null)
          cursor.close();

        cursor = db.query("sms", null,"body LIKE ?", new String[] {PROCESSED_KEY_EXCHANGE + "%"},
                          null, null, null);

        updateSmsBodyAndType(db, cursor, PROCESSED_KEY_EXCHANGE, 0x8000L | 0x2000L);

        if (cursor != null)
          cursor.close();

        cursor = db.query("sms", null,"body LIKE ?", new String[] {STALE_KEY_EXCHANGE + "%"},
                          null, null, null);

        updateSmsBodyAndType(db, cursor, STALE_KEY_EXCHANGE, 0x8000L | 0x4000L);

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

        if (cursor != null)
          cursor.close();

        cursor = db.query("thread", null,"snippet LIKE ?",
                          new String[] {SYMMETRIC_ENCRYPT + "%"}, null, null, null);

        updateThreadSnippetAndType(db, cursor, SYMMETRIC_ENCRYPT, 0x80000000L);

        if (cursor != null)
          cursor.close();

        cursor = db.query("thread", null,"snippet LIKE ?",
                          new String[] {KEY_EXCHANGE + "%"}, null, null, null);

        updateThreadSnippetAndType(db, cursor, KEY_EXCHANGE, 0x8000L);

        if (cursor != null)
          cursor.close();

        cursor = db.query("thread", null,"snippet LIKE ?",
                          new String[] {STALE_KEY_EXCHANGE + "%"}, null, null, null);

        updateThreadSnippetAndType(db, cursor, STALE_KEY_EXCHANGE, 0x8000L | 0x4000L);

        if (cursor != null)
          cursor.close();

        cursor = db.query("thread", null,"snippet LIKE ?",
                          new String[] {PROCESSED_KEY_EXCHANGE + "%"}, null, null, null);

        updateThreadSnippetAndType(db, cursor, KEY_EXCHANGE, 0x8000L | 0x2000L);

        if (cursor != null)
          cursor.close();
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

          if (!Util.isEmpty(address)) {
            db.execSQL("UPDATE mms SET address = ? WHERE _id = ?", new String[]{address, mmsId+""});
          }
        }

        if (cursor != null)
          cursor.close();
      }

      db.setTransactionSuccessful();
      db.endTransaction();
    }

    private void updateSmsBodyAndType(SQLiteDatabase db, Cursor cursor, String prefix, long typeMask)
    {
      while (cursor != null && cursor.moveToNext()) {
        String body = cursor.getString(cursor.getColumnIndexOrThrow("body"));
        long type   = cursor.getLong(cursor.getColumnIndexOrThrow("type"));
        long id     = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));

        if (body.startsWith(prefix)) {
          body  = body.substring(prefix.length());
          type |= typeMask;

          db.execSQL("UPDATE sms SET type = ?, body = ? WHERE _id = ?",
                     new String[]{type+"", body, id+""});
        }
      }

      if (cursor != null)
        cursor.close();
    }

    private void updateThreadSnippetAndType(SQLiteDatabase db, Cursor cursor, String prefix, long typeMask)
    {
      while (cursor != null && cursor.moveToNext()) {
        String snippet = cursor.getString(cursor.getColumnIndexOrThrow("snippet"));
        long type      = cursor.getLong(cursor.getColumnIndexOrThrow("snippet_type"));
        long id       = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));

        if (snippet.startsWith(prefix)) {
          snippet = snippet.substring(prefix.length());
          type   |= typeMask;

          db.execSQL("UPDATE thread SET snippet_type = ?, snippet = ? WHERE _id = ?",
                     new String[]{type+"", snippet, id+""});
        }
      }

      if (cursor != null)
        cursor.close();
    }

    private void executeStatements(SQLiteDatabase db, String[] statements) {
      for (String statement : statements)
        db.execSQL(statement);
    }

  }
}
