/**
 * Copyright (C) 2014 Open Whisper Systems
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
package org.thoughtcrime.securesms.jobmanager.persistence;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.thoughtcrime.securesms.jobmanager.EncryptionKeys;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.dependencies.AggregateDependencyInjector;
import org.thoughtcrime.securesms.logging.Log;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class PersistentStorage {

  private static final int DATABASE_VERSION = 1;

  private static final String TABLE_NAME = "queue";
  private static final String ID         = "_id";
  private static final String ITEM       = "item";
  private static final String ENCRYPTED  = "encrypted";

  private static final String DATABASE_CREATE = String.format("CREATE TABLE %s (%s INTEGER PRIMARY KEY, %s TEXT NOT NULL, %s INTEGER DEFAULT 0);",
                                                              TABLE_NAME, ID, ITEM, ENCRYPTED);

  private final Context                     context;
  private final DatabaseHelper              databaseHelper;
  private final JobSerializer               jobSerializer;
  private final AggregateDependencyInjector dependencyInjector;

  public PersistentStorage(Context context, String name,
                           JobSerializer serializer,
                           AggregateDependencyInjector dependencyInjector)
  {
    this.databaseHelper     = new DatabaseHelper(context, "_jobqueue-" + name);
    this.context            = context;
    this.jobSerializer      = serializer;
    this.dependencyInjector = dependencyInjector;
  }

  public void store(Job job) throws IOException {
    ContentValues contentValues = new ContentValues();
    contentValues.put(ITEM, jobSerializer.serialize(job));
    contentValues.put(ENCRYPTED, job.getEncryptionKeys() != null);

    long id = databaseHelper.getWritableDatabase().insert(TABLE_NAME, null, contentValues);
    job.setPersistentId(id);
  }

  public List<Job> getAllUnencrypted() {
    return getJobs(null, ENCRYPTED + " = 0");
  }

  public List<Job> getAllEncrypted(EncryptionKeys keys) {
    return getJobs(keys, ENCRYPTED + " = 1");
  }

  private List<Job> getJobs(EncryptionKeys keys, String where) {
    List<Job>      results  = new LinkedList<>();
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    Cursor         cursor   = null;

    try {
      cursor = database.query(TABLE_NAME, null, where, null, null, null, ID + " ASC", null);

      while (cursor.moveToNext()) {
        long    id        = cursor.getLong(cursor.getColumnIndexOrThrow(ID));
        String  item      = cursor.getString(cursor.getColumnIndexOrThrow(ITEM));
        boolean encrypted = cursor.getInt(cursor.getColumnIndexOrThrow(ENCRYPTED)) == 1;

        try{
          Job job = jobSerializer.deserialize(keys, encrypted, item);

          job.setPersistentId(id);
          job.setEncryptionKeys(keys);
          dependencyInjector.injectDependencies(context, job);

          results.add(job);
        } catch (IOException e) {
          Log.w("PersistentStore", e);
          remove(id);
        }
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }

    return results;
  }

  public void remove(long id) {
    databaseHelper.getWritableDatabase()
                  .delete(TABLE_NAME, ID + " = ?", new String[] {String.valueOf(id)});
  }

  private static class DatabaseHelper extends SQLiteOpenHelper {

    public DatabaseHelper(Context context, String name) {
      super(context, name, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
      db.execSQL(DATABASE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }
  }

}
