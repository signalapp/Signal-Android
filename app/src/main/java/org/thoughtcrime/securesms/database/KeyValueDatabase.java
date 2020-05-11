package org.thoughtcrime.securesms.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;

import net.sqlcipher.database.SQLiteDatabase;

import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;
import org.thoughtcrime.securesms.keyvalue.KeyValueDataSet;

import java.util.Collection;
import java.util.Map;

public class KeyValueDatabase extends Database {

  public static final String TABLE_NAME = "key_value";

  private static final String ID    = "_id";
  private static final String KEY   = "key";
  private static final String VALUE = "value";
  private static final String TYPE  = "type";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + "(" + ID    + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                                                                 KEY   + " TEXT UNIQUE, " +
                                                                                 VALUE + " TEXT, " +
                                                                                 TYPE  + " INTEGER)";

  KeyValueDatabase(Context context, SQLCipherOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  public @NonNull KeyValueDataSet getDataSet() {
    KeyValueDataSet dataSet = new KeyValueDataSet();

    try (Cursor cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME, null, null, null, null, null, null)){
      while (cursor != null && cursor.moveToNext()) {
        Type type = Type.fromId(cursor.getInt(cursor.getColumnIndexOrThrow(TYPE)));
        String key = cursor.getString(cursor.getColumnIndexOrThrow(KEY));

        switch (type) {
          case BLOB:
            dataSet.putBlob(key, cursor.getBlob(cursor.getColumnIndexOrThrow(VALUE)));
            break;
          case BOOLEAN:
            dataSet.putBoolean(key, cursor.getInt(cursor.getColumnIndexOrThrow(VALUE)) == 1);
            break;
          case FLOAT:
            dataSet.putFloat(key, cursor.getFloat(cursor.getColumnIndexOrThrow(VALUE)));
            break;
          case INTEGER:
            dataSet.putInteger(key, cursor.getInt(cursor.getColumnIndexOrThrow(VALUE)));
            break;
          case LONG:
            dataSet.putLong(key, cursor.getLong(cursor.getColumnIndexOrThrow(VALUE)));
            break;
          case STRING:
            dataSet.putString(key, cursor.getString(cursor.getColumnIndexOrThrow(VALUE)));
            break;
        }
      }
    }

    return dataSet;
  }

  public void writeDataSet(@NonNull KeyValueDataSet dataSet, @NonNull Collection<String> removes) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();

    db.beginTransaction();
    try {
      for (Map.Entry<String, Object> entry : dataSet.getValues().entrySet()) {
        String key   = entry.getKey();
        Object value = entry.getValue();
        Class  type  = dataSet.getType(key);

        ContentValues contentValues = new ContentValues(3);
        contentValues.put(KEY, key);

        if (type == byte[].class) {
          contentValues.put(VALUE, (byte[]) value);
          contentValues.put(TYPE, Type.BLOB.getId());
        } else if (type == Boolean.class) {
          contentValues.put(VALUE, (boolean) value);
          contentValues.put(TYPE, Type.BOOLEAN.getId());
        } else if (type == Float.class) {
          contentValues.put(VALUE, (float) value);
          contentValues.put(TYPE, Type.FLOAT.getId());
        } else if (type == Integer.class) {
          contentValues.put(VALUE, (int) value);
          contentValues.put(TYPE, Type.INTEGER.getId());
        } else if (type == Long.class) {
          contentValues.put(VALUE, (long) value);
          contentValues.put(TYPE, Type.LONG.getId());
        } else if (type == String.class) {
          contentValues.put(VALUE, (String) value);
          contentValues.put(TYPE, Type.STRING.getId());
        } else {
          throw new AssertionError("Unknown type: " + type);
        }

        db.insertWithOnConflict(TABLE_NAME, null, contentValues, SQLiteDatabase.CONFLICT_REPLACE);
      }

      String deleteQuery = KEY + " = ?";
      for (String remove : removes) {
        db.delete(TABLE_NAME, deleteQuery, new String[] { remove });
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  private enum Type {
    BLOB(0), BOOLEAN(1), FLOAT(2), INTEGER(3), LONG(4), STRING(5);

    final int id;

    Type(int id) {
      this.id = id;
    }

    public int getId() {
      return id;
    }

    public static Type fromId(int id) {
      return values()[id];
    }
  }
}
