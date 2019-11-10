package org.thoughtcrime.securesms.util;

import android.database.Cursor;

import androidx.annotation.NonNull;

import net.sqlcipher.database.SQLiteDatabase;

public final class SqlUtil {
  private SqlUtil() {}


  public static boolean tableExists(@NonNull SQLiteDatabase db, @NonNull String table) {
    try (Cursor cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type=? AND name=?", new String[] { "table", table })) {
      return cursor != null && cursor.moveToNext();
    }
  }

  public static boolean columnExists(@NonNull SQLiteDatabase db, @NonNull String table, @NonNull String column) {
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
