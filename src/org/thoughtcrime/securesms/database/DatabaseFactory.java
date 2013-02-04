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
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;

import org.thoughtcrime.securesms.crypto.MasterSecret;

public class DatabaseFactory {

  private static final int INTRODUCED_IDENTITIES_VERSION = 2;
  private static final int INTRODUCED_INDEXES_VERSION    = 3;
  private static final int INTRODUCED_DATE_SENT_VERSION  = 4;
  private static final int INTRODUCED_DRAFTS_VERSION     = 5;
  private static final int DATABASE_VERSION              = 5;

  private static final String DATABASE_NAME    = "messages.db";
  private static final Object lock             = new Object();

  private static DatabaseFactory instance;
  private static EncryptingMmsDatabase encryptingMmsInstance;
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

  public static EncryptingMmsDatabase getEncryptingMmsDatabase(Context context, MasterSecret masterSecret) {
    synchronized (lock) {
      if (encryptingMmsInstance == null) {
        DatabaseFactory factory = getInstance(context);
        encryptingMmsInstance   = new EncryptingMmsDatabase(context, factory.databaseHelper, masterSecret);
      }

      return encryptingMmsInstance;
    }
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

      //  db.execSQL(CanonicalAddress.CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
      if (oldVersion < INTRODUCED_IDENTITIES_VERSION) {
        db.execSQL(IdentityDatabase.CREATE_TABLE);
      }

      if (oldVersion < INTRODUCED_INDEXES_VERSION) {
        executeStatements(db, SmsDatabase.CREATE_INDEXS);
        executeStatements(db, MmsDatabase.CREATE_INDEXS);
        executeStatements(db, PartDatabase.CREATE_INDEXS);
        executeStatements(db, ThreadDatabase.CREATE_INDEXS);
        executeStatements(db, MmsAddressDatabase.CREATE_INDEXS);
      }

      if (oldVersion < INTRODUCED_DATE_SENT_VERSION) {
        db.beginTransaction();
        db.execSQL("ALTER TABLE " + SmsDatabase.TABLE_NAME +
                   " ADD COLUMN " + SmsDatabase.DATE_SENT + " INTEGER;");
        db.execSQL("UPDATE " + SmsDatabase.TABLE_NAME +
                   " SET " + SmsDatabase.DATE_SENT + " = " + SmsDatabase.DATE_RECEIVED + ";");

        db.execSQL("ALTER TABLE " + MmsDatabase.TABLE_NAME +
                   " ADD COLUMN " + MmsDatabase.DATE_RECEIVED + " INTEGER;");
        db.execSQL("UPDATE " + MmsDatabase.TABLE_NAME +
                   " SET " + MmsDatabase.DATE_RECEIVED + " = " + MmsDatabase.DATE_SENT + ";");
        db.setTransactionSuccessful();
        db.endTransaction();
      }

      if (oldVersion < INTRODUCED_DRAFTS_VERSION) {
        db.beginTransaction();
        db.execSQL(DraftDatabase.CREATE_TABLE);
        executeStatements(db, DraftDatabase.CREATE_INDEXS);
        db.setTransactionSuccessful();
        db.endTransaction();
      }
    }

    private void executeStatements(SQLiteDatabase db, String[] statements) {
      for (String statement : statements)
        db.execSQL(statement);
    }

  }
}
