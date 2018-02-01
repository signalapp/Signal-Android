package org.thoughtcrime.securesms.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class RawDatabase extends Database {
  public RawDatabase(Context context, SQLiteOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  public SQLiteDatabase getWritableDatabase() {
    return databaseHelper.getWritableDatabase();
  }
}
