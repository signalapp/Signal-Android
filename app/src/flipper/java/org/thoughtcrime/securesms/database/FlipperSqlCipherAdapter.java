package org.thoughtcrime.securesms.database;

import android.app.Application;
import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.flipper.plugins.databases.DatabaseDescriptor;
import com.facebook.flipper.plugins.databases.DatabaseDriver;

import net.sqlcipher.DatabaseUtils;
import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteStatement;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A lot of this code is taken from {@link com.facebook.flipper.plugins.databases.impl.SqliteDatabaseDriver}
 * and made to work with SqlCipher. Unfortunately I couldn't use it directly, nor subclass it.
 */
public class FlipperSqlCipherAdapter extends DatabaseDriver<FlipperSqlCipherAdapter.Descriptor> {

  private static final String TAG = Log.tag(FlipperSqlCipherAdapter.class);

  public FlipperSqlCipherAdapter(Context context) {
    super(context);
  }

  @Override
  public List<Descriptor> getDatabases() {
    try {
      Field databaseHelperField = DatabaseFactory.class.getDeclaredField("databaseHelper");
      databaseHelperField.setAccessible(true);

      SignalDatabase mainOpenHelper       = Objects.requireNonNull((SQLCipherOpenHelper) databaseHelperField.get(DatabaseFactory.getInstance(getContext())));
      SignalDatabase keyValueOpenHelper   = KeyValueDatabase.getInstance((Application) getContext());
      SignalDatabase megaphoneOpenHelper  = MegaphoneDatabase.getInstance((Application) getContext());
      SignalDatabase jobManagerOpenHelper = JobDatabase.getInstance((Application) getContext());

      return Arrays.asList(new Descriptor(mainOpenHelper),
                           new Descriptor(keyValueOpenHelper),
                           new Descriptor(megaphoneOpenHelper),
                           new Descriptor(jobManagerOpenHelper));
    } catch (Exception e) {
      Log.i(TAG, "Unable to use reflection to access raw database.", e);
    }
    return Collections.emptyList();
  }

  @Override
  public List<String> getTableNames(Descriptor descriptor) {
    SQLiteDatabase db         = descriptor.getReadable();
    List<String>   tableNames = new ArrayList<>();

    try (Cursor cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type IN (?, ?)", new String[] { "table", "view" })) {
      while (cursor != null && cursor.moveToNext()) {
        tableNames.add(cursor.getString(0));
      }
    }

    return tableNames;
  }

  @Override
  public DatabaseGetTableDataResponse getTableData(Descriptor descriptor, String table, String order, boolean reverse, int start, int count) {
    SQLiteDatabase db = descriptor.getReadable();

    long   total   = DatabaseUtils.queryNumEntries(db, table);
    String orderBy = order != null ? order + (reverse ? " DESC" : " ASC") : null;
    String limitBy = start + ", " + count;

    try (Cursor cursor = db.query(table, null, null, null, null, null, orderBy, limitBy)) {
      String[]           columnNames = cursor.getColumnNames();
      List<List<Object>> rows        = cursorToList(cursor);

      return new DatabaseGetTableDataResponse(Arrays.asList(columnNames), rows, start, rows.size(), total);
    }
  }

  @Override
  public DatabaseGetTableStructureResponse getTableStructure(Descriptor descriptor, String table) {
    SQLiteDatabase db = descriptor.getReadable();

    Map<String, String> foreignKeyValues = new HashMap<>();

    try(Cursor cursor = db.rawQuery("PRAGMA foreign_key_list(" + table + ")", null)) {
      while (cursor != null && cursor.moveToNext()) {
        String from      = cursor.getString(cursor.getColumnIndex("from"));
        String to        = cursor.getString(cursor.getColumnIndex("to"));
        String tableName = cursor.getString(cursor.getColumnIndex("table")) + "(" + to + ")";

        foreignKeyValues.put(from, tableName);
      }
    }


    List<String>        structureColumns = Arrays.asList("column_name", "data_type", "nullable", "default", "primary_key", "foreign_key");
    List<List<Object>>  structureValues = new ArrayList<>();

    try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
      while (cursor != null && cursor.moveToNext()) {
        String columnName = cursor.getString(cursor.getColumnIndex("name"));
        String foreignKey = foreignKeyValues.containsKey(columnName) ? foreignKeyValues.get(columnName) : null;

        structureValues.add(Arrays.asList(columnName,
                                          cursor.getString(cursor.getColumnIndex("type")),
                                          cursor.getInt(cursor.getColumnIndex("notnull")) == 0,
                                          getObjectFromColumnIndex(cursor, cursor.getColumnIndex("dflt_value")),
                                          cursor.getInt(cursor.getColumnIndex("pk")) == 1,
                                          foreignKey));
      }
    }


    List<String>       indexesColumns = Arrays.asList("index_name", "unique", "indexed_column_name");
    List<List<Object>> indexesValues  = new ArrayList<>();

    try (Cursor indexesCursor = db.rawQuery("PRAGMA index_list(" + table + ")", null)) {
      List<String> indexedColumnNames = new ArrayList<>();
      String       indexName          = indexesCursor.getString(indexesCursor.getColumnIndex("name"));

      try(Cursor indexInfoCursor = db.rawQuery("PRAGMA index_info(" + indexName + ")", null)) {
        while (indexInfoCursor.moveToNext()) {
          indexedColumnNames.add(indexInfoCursor.getString(indexInfoCursor.getColumnIndex("name")));
        }
      }

      indexesValues.add(Arrays.asList(indexName,
                                      indexesCursor.getInt(indexesCursor.getColumnIndex("unique")) == 1,
                                      TextUtils.join(",", indexedColumnNames)));

    }

    return new DatabaseGetTableStructureResponse(structureColumns, structureValues, indexesColumns, indexesValues);
  }

  @Override
  public DatabaseGetTableInfoResponse getTableInfo(Descriptor databaseDescriptor, String table) {
    SQLiteDatabase db = databaseDescriptor.getReadable();

    try (Cursor cursor = db.rawQuery("SELECT sql FROM sqlite_master WHERE name = ?", new String[] { table })) {
      cursor.moveToFirst();
      return new DatabaseGetTableInfoResponse(cursor.getString(cursor.getColumnIndex("sql")));
    }
  }

  @Override
  public DatabaseExecuteSqlResponse executeSQL(Descriptor descriptor, String query) {
    SQLiteDatabase db = descriptor.getWritable();

    String firstWordUpperCase = getFirstWord(query).toUpperCase();

    switch (firstWordUpperCase) {
      case "UPDATE":
      case "DELETE":
        return executeUpdateDelete(db, query);
      case "INSERT":
        return executeInsert(db, query);
      case "SELECT":
      case "PRAGMA":
      case "EXPLAIN":
        return executeSelect(db, query);
      default:
        return executeRawQuery(db, query);
    }
  }

  private static String getFirstWord(String s) {
    s = s.trim();
    int firstSpace = s.indexOf(' ');
    return firstSpace >= 0 ? s.substring(0, firstSpace) : s;
  }

  private static DatabaseExecuteSqlResponse executeUpdateDelete(SQLiteDatabase database, String query) {
    SQLiteStatement statement = database.compileStatement(query);
    int             count     = statement.executeUpdateDelete();

    return DatabaseExecuteSqlResponse.successfulUpdateDelete(count);
  }

  private static DatabaseExecuteSqlResponse executeInsert(SQLiteDatabase database, String query) {
    SQLiteStatement statement  = database.compileStatement(query);
    long            insertedId = statement.executeInsert();

    return DatabaseExecuteSqlResponse.successfulInsert(insertedId);
  }

  private static DatabaseExecuteSqlResponse executeSelect(SQLiteDatabase database, String query) {
    try (Cursor cursor = database.rawQuery(query, null)) {
      String[]           columnNames = cursor.getColumnNames();
      List<List<Object>> rows        = cursorToList(cursor);

      return DatabaseExecuteSqlResponse.successfulSelect(Arrays.asList(columnNames), rows);
    }
  }

  private static DatabaseExecuteSqlResponse executeRawQuery(SQLiteDatabase database, String query) {
    database.execSQL(query);
    return DatabaseExecuteSqlResponse.successfulRawQuery();
  }

  private static @NonNull List<List<Object>> cursorToList(Cursor cursor) {
    List<List<Object>> rows       = new ArrayList<>();
    int                numColumns = cursor.getColumnCount();

    while (cursor.moveToNext()) {
      List<Object> values = new ArrayList<>(numColumns);

      for (int column = 0; column < numColumns; column++) {
        values.add(getObjectFromColumnIndex(cursor, column));
      }

      rows.add(values);
    }
    return rows;
  }

  private static @Nullable Object getObjectFromColumnIndex(Cursor cursor, int column) {
    switch (cursor.getType(column)) {
      case Cursor.FIELD_TYPE_NULL:
        return null;
      case Cursor.FIELD_TYPE_INTEGER:
        return cursor.getLong(column);
      case Cursor.FIELD_TYPE_FLOAT:
        return cursor.getDouble(column);
      case Cursor.FIELD_TYPE_BLOB:
        return cursor.getBlob(column);
      case Cursor.FIELD_TYPE_STRING:
      default:
        return cursor.getString(column);
    }
  }

  static class Descriptor implements DatabaseDescriptor {
    private final SignalDatabase sqlCipherOpenHelper;

    Descriptor(@NonNull SignalDatabase sqlCipherOpenHelper) {
      this.sqlCipherOpenHelper = sqlCipherOpenHelper;
    }

    @Override
    public String name() {
      return sqlCipherOpenHelper.getDatabaseName();
    }

    public @NonNull SQLiteDatabase getReadable() {
      return sqlCipherOpenHelper.getSqlCipherDatabase();
    }

    public @NonNull SQLiteDatabase getWritable() {
      return sqlCipherOpenHelper.getSqlCipherDatabase();
    }
  }
}
