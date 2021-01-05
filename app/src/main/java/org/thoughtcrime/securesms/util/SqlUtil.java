package org.thoughtcrime.securesms.util;

import android.content.ContentValues;
import android.database.Cursor;

import androidx.annotation.NonNull;

import net.sqlcipher.database.SQLiteDatabase;

import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.libsignal.util.guava.Preconditions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SqlUtil {
  private SqlUtil() {}


  public static boolean tableExists(@NonNull SQLiteDatabase db, @NonNull String table) {
    try (Cursor cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type=? AND name=?", new String[] { "table", table })) {
      return cursor != null && cursor.moveToNext();
    }
  }

  public static boolean isEmpty(@NonNull SQLiteDatabase db, @NonNull String table) {
    try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + table, null)) {
      if (cursor.moveToFirst()) {
        return cursor.getInt(0) == 0;
      } else {
        return true;
      }
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

  public static String[] buildArgs(Object... objects) {
    String[] args = new String[objects.length];

    for (int i = 0; i < objects.length; i++) {
      if (objects[i] == null) {
        throw new NullPointerException("Cannot have null arg!");
      } else if (objects[i] instanceof RecipientId) {
        args[i] = ((RecipientId) objects[i]).serialize();
      } else {
        args[i] = objects[i].toString();
      }
    }

    return args;
  }

  /**
   * Returns an updated query and args pairing that will only update rows that would *actually*
   * change. In other words, if {@link SQLiteDatabase#update(String, ContentValues, String, String[])}
   * returns > 0, then you know something *actually* changed.
   */
  public static @NonNull Query buildTrueUpdateQuery(@NonNull String selection,
                                                    @NonNull String[] args,
                                                    @NonNull ContentValues contentValues)
  {
    StringBuilder                  qualifier = new StringBuilder();
    Set<Map.Entry<String, Object>> valueSet  = contentValues.valueSet();
    List<String>                   fullArgs  = new ArrayList<>(args.length + valueSet.size());

    fullArgs.addAll(Arrays.asList(args));

    int i = 0;

    for (Map.Entry<String, Object> entry : valueSet) {
      if (entry.getValue() != null) {
        qualifier.append(entry.getKey()).append(" != ? OR ").append(entry.getKey()).append(" IS NULL");
        fullArgs.add(String.valueOf(entry.getValue()));
      } else {
        qualifier.append(entry.getKey()).append(" NOT NULL");
      }

      if (i != valueSet.size() - 1) {
        qualifier.append(" OR ");
      }

      i++;
    }

    return new Query("(" + selection + ") AND (" + qualifier + ")", fullArgs.toArray(new String[0]));
  }

  public static @NonNull Query buildCollectionQuery(@NonNull String column, @NonNull Collection<? extends Object> values) {
    Preconditions.checkArgument(values.size() > 0);

    StringBuilder query = new StringBuilder();
    Object[]      args  = new Object[values.size()];

    int i = 0;

    for (Object value : values) {
      query.append("?");
      args[i] = value;

      if (i != values.size() - 1) {
        query.append(", ");
      }

      i++;
    }

    return new Query(column + " IN (" + query.toString() + ")", buildArgs(args));
  }

  public static String[] appendArg(@NonNull String[] args, String addition) {
    String[] output = new String[args.length + 1];

    System.arraycopy(args, 0, output, 0, args.length);
    output[output.length - 1] = addition;

    return output;
  }

  public static class Query {
    private final String   where;
    private final String[] whereArgs;

    private Query(@NonNull String where, @NonNull String[] whereArgs) {
      this.where     = where;
      this.whereArgs = whereArgs;
    }

    public String getWhere() {
      return where;
    }

    public String[] getWhereArgs() {
      return whereArgs;
    }
  }
}
