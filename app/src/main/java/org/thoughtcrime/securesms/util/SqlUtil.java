package org.thoughtcrime.securesms.util;

import android.content.ContentValues;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.annimon.stream.Stream;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.thoughtcrime.securesms.database.model.DatabaseId;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.libsignal.util.guava.Preconditions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class SqlUtil {

  /** The maximum number of arguments (i.e. question marks) allowed in a SQL statement. */
  private static final int MAX_QUERY_ARGS = 999;

  private SqlUtil() {}

  public static boolean tableExists(@NonNull SQLiteDatabase db, @NonNull String table) {
    try (Cursor cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type=? AND name=?", new String[] { "table", table })) {
      return cursor != null && cursor.moveToNext();
    }
  }

  public static @NonNull List<String> getAllTables(@NonNull SQLiteDatabase db) {
    List<String> tables = new LinkedList<>();

    try (Cursor cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type=?", new String[] { "table" })) {
      while (cursor.moveToNext()) {
        tables.add(cursor.getString(0));
      }
    }

    return tables;
  }

  /**
   * Splits a multi-statement SQL block into independent statements. It is assumed that there is
   * only one statement per line, and that each statement is terminated by a semi-colon.
   */
  public static @NonNull List<String> splitStatements(@NonNull String sql) {
    return Stream.of(Arrays.asList(sql.split(";\n")))
                 .map(String::trim)
                 .toList();
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
      } else if (objects[i] instanceof DatabaseId) {
        args[i] = ((DatabaseId) objects[i]).serialize();
      } else {
        args[i] = objects[i].toString();
      }
    }

    return args;
  }

  public static String[] buildArgs(long argument) {
    return new String[] { Long.toString(argument) };
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

  public static @NonNull List<Query> buildCustomCollectionQuery(@NonNull String query, @NonNull List<String[]> argList) {
    return buildCustomCollectionQuery(query, argList, MAX_QUERY_ARGS);
  }

  @VisibleForTesting
  static @NonNull List<Query> buildCustomCollectionQuery(@NonNull String query, @NonNull List<String[]> argList, int maxQueryArgs) {
    int batchSize = maxQueryArgs / argList.get(0).length;

    return Util.chunk(argList, batchSize)
               .stream()
               .map(argBatch -> buildSingleCustomCollectionQuery(query, argBatch))
               .collect(Collectors.toList());
  }

  private static @NonNull Query buildSingleCustomCollectionQuery(@NonNull String query, @NonNull List<String[]> argList) {
    StringBuilder outputQuery = new StringBuilder();
    String[]      outputArgs  = new String[argList.get(0).length * argList.size()];
    int           argPosition = 0;

    for (int i = 0, len = argList.size(); i < len; i++) {
      outputQuery.append("(").append(query).append(")");
      if (i < len - 1) {
        outputQuery.append(" OR ");
      }

      String[] args = argList.get(i);
      for (String arg : args) {
        outputArgs[argPosition] = arg;
        argPosition++;
      }
    }

    return new Query(outputQuery.toString(), outputArgs);
  }

  public static @NonNull Query buildQuery(@NonNull String where, @NonNull Object... args) {
    return new SqlUtil.Query(where, SqlUtil.buildArgs(args));
  }

  public static String[] appendArg(@NonNull String[] args, String addition) {
    String[] output = new String[args.length + 1];

    System.arraycopy(args, 0, output, 0, args.length);
    output[output.length - 1] = addition;

    return output;
  }

  public static List<Query> buildBulkInsert(@NonNull String tableName, @NonNull String[] columns, List<ContentValues> contentValues) {
    return buildBulkInsert(tableName, columns, contentValues, MAX_QUERY_ARGS);
  }

  @VisibleForTesting
  static List<Query> buildBulkInsert(@NonNull String tableName, @NonNull String[] columns, List<ContentValues> contentValues, int maxQueryArgs) {
    int batchSize = maxQueryArgs / columns.length;

    return Util.chunk(contentValues, batchSize)
               .stream()
               .map(batch -> buildSingleBulkInsert(tableName, columns, batch))
               .collect(Collectors.toList());
  }

  private static Query buildSingleBulkInsert(@NonNull String tableName, @NonNull String[] columns, List<ContentValues> contentValues) {
    StringBuilder builder = new StringBuilder();
    builder.append("INSERT INTO ").append(tableName).append(" (");

    for (int i = 0; i < columns.length; i++) {
      builder.append(columns[i]);
      if (i < columns.length - 1) {
        builder.append(", ");
      }
    }

    builder.append(") VALUES ");

    StringBuilder placeholder = new StringBuilder();
    placeholder.append("(");

    for (int i = 0; i < columns.length; i++) {
      placeholder.append("?");
      if (i < columns.length - 1) {
        placeholder.append(", ");
      }
    }

    placeholder.append(")");


    for (int i = 0, len = contentValues.size(); i < len; i++) {
      builder.append(placeholder);
      if (i < len - 1) {
        builder.append(", ");
      }
    }

    String   query = builder.toString();
    String[] args  = new String[columns.length * contentValues.size()];

    int i = 0;
    for (ContentValues values :  contentValues) {
      for (String column : columns) {
        Object value = values.get(column);
        args[i] = value != null ? values.get(column).toString() : "null";
        i++;
      }
    }

    return new Query(query, args);
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
