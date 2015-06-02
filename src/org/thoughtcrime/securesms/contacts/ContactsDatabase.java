/**
 * Copyright (C) 2013 Open Whisper Systems
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
package org.thoughtcrime.securesms.contacts;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.NumberUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Database to supply all types of contacts that TextSecure needs to know about
 *
 * @author Jake McGinty
 */
public class ContactsDatabase {
  private static final String TAG = ContactsDatabase.class.getSimpleName();
  private final DatabaseOpenHelper dbHelper;
  private final Context            context;

  public static final String TABLE_NAME         = "CONTACTS";
  public static final String ID_COLUMN          = ContactsContract.CommonDataKinds.Phone._ID;
  public static final String NAME_COLUMN        = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME;
  public static final String NUMBER_TYPE_COLUMN = ContactsContract.CommonDataKinds.Phone.TYPE;
  public static final String NUMBER_COLUMN      = ContactsContract.CommonDataKinds.Phone.NUMBER;
  public static final String LABEL_COLUMN       = ContactsContract.CommonDataKinds.Phone.LABEL;
  public static final String TYPE_COLUMN        = "type";

  private static final String   FILTER_SELECTION   = NAME_COLUMN + " LIKE ? OR " + NUMBER_COLUMN + " LIKE ?";
  private static final String   CONTACT_LIST_SORT  = NAME_COLUMN + " COLLATE NOCASE ASC";
  private static final String[] ANDROID_PROJECTION = new String[]{ID_COLUMN,
                                                                  NAME_COLUMN,
                                                                  NUMBER_TYPE_COLUMN,
                                                                  LABEL_COLUMN,
                                                                  NUMBER_COLUMN};

  private static final String[] CONTACTS_PROJECTION = new String[]{ID_COLUMN,
                                                                   NAME_COLUMN,
                                                                   NUMBER_TYPE_COLUMN,
                                                                   LABEL_COLUMN,
                                                                   NUMBER_COLUMN,
                                                                   TYPE_COLUMN};

  public static final int NORMAL_TYPE = 0;
  public static final int PUSH_TYPE   = 1;
  public static final int GROUP_TYPE  = 2;

  public ContactsDatabase(Context context) {
    this.dbHelper = new DatabaseOpenHelper(context);
    this.context  = context;
  }

  public void close() {
    dbHelper.close();
  }

  public Cursor query(String filter, boolean pushOnly) {
    // FIXME: This doesn't make sense to me.  You pass in pushOnly, but then
    // conditionally check to see whether other contacts should be included
    // in the query method itself? I don't think this method should have any
    // understanding of that stuff.
    final boolean      includeAndroidContacts = !pushOnly && TextSecurePreferences.isSmsEnabled(context);
    final Cursor       localCursor            = queryLocalDb(filter);
    final Cursor       androidCursor;
    final MatrixCursor newNumberCursor;

    if (includeAndroidContacts) {
      androidCursor = queryAndroidDb(filter);
    } else {
      androidCursor = null;
    }

    if (!TextUtils.isEmpty(filter) && NumberUtil.isValidSmsOrEmail(filter)) {
      newNumberCursor = new MatrixCursor(CONTACTS_PROJECTION, 1);
      newNumberCursor.addRow(new Object[]{-1L, context.getString(R.string.contact_selection_list__unknown_contact),
                             ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM, "\u21e2", filter, NORMAL_TYPE});
    } else {
      newNumberCursor = null;
    }

    List<Cursor> cursors = new ArrayList<Cursor>();
    if (localCursor != null)     cursors.add(localCursor);
    if (androidCursor != null)   cursors.add(androidCursor);
    if (newNumberCursor != null) cursors.add(newNumberCursor);

    switch (cursors.size()) {
    case 0: return null;
    case 1: return cursors.get(0);
    default: return new MergeCursor(cursors.toArray(new Cursor[]{}));
    }
  }

  private Cursor queryAndroidDb(String filter) {
    final Uri baseUri;
    if (!TextUtils.isEmpty(filter)) {
      baseUri = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI,
                                     Uri.encode(filter));
    } else {
      baseUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
    }
    Cursor cursor = context.getContentResolver().query(baseUri, ANDROID_PROJECTION, null, null, CONTACT_LIST_SORT);
    return cursor == null ? null : new TypedCursorWrapper(cursor);
  }

  private Cursor queryLocalDb(String filter) {
    final String   selection;
    final String[] selectionArgs;
    final String   fuzzyFilter = "%" + filter + "%";
    if (!TextUtils.isEmpty(filter)) {
      selection     = FILTER_SELECTION;
      selectionArgs = new String[]{fuzzyFilter, fuzzyFilter};
    } else {
      selection     = null;
      selectionArgs = null;
    }
    return queryLocalDb(selection, selectionArgs, null);
  }

  private Cursor queryLocalDb(String selection, String[] selectionArgs, String[] columns) {
    SQLiteDatabase localDb = dbHelper.getReadableDatabase();
    final Cursor localCursor;
    if (localDb != null) localCursor = localDb.query(TABLE_NAME, columns, selection, selectionArgs, null, null, CONTACT_LIST_SORT);
    else                 localCursor = null;
    if (localCursor != null && !localCursor.moveToFirst()) {
      localCursor.close();
      return null;
    }
    return localCursor;
  }

  private static class DatabaseOpenHelper extends SQLiteOpenHelper {

    private final Context        context;
    private       SQLiteDatabase mDatabase;

    private static final String TABLE_CREATE =
        "CREATE TABLE " + TABLE_NAME + " (" +
            ID_COLUMN          + " INTEGER PRIMARY KEY, " +
            NAME_COLUMN        + " TEXT, " +
            NUMBER_TYPE_COLUMN + " INTEGER, " +
            LABEL_COLUMN       + " TEXT, " +
            NUMBER_COLUMN      + " TEXT, " +
            TYPE_COLUMN        + " INTEGER);";

    DatabaseOpenHelper(Context context) {
      super(context, null, null, 1);
      this.context = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
      Log.d(TAG, "onCreate called for contacts database.");
      mDatabase = db;
      mDatabase.execSQL(TABLE_CREATE);
      if (TextSecurePreferences.isPushRegistered(context)) {
        try {
          loadPushUsers();
        } catch (IOException ioe) {
          Log.e(TAG, "Issue when trying to load push users into memory db.", ioe);
        }
      }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
      Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
          + newVersion + ", which will destroy all old data");
      db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
      onCreate(db);
    }

    private void loadPushUsers() throws IOException {
      Log.d(TAG, "populating push users into virtual db.");
      Collection<ContactAccessor.ContactData> pushUsers = ContactAccessor.getInstance().getContactsWithPush(context);
      for (ContactAccessor.ContactData user : pushUsers) {
        ContentValues values = new ContentValues();
        values.put(ID_COLUMN, user.id);
        values.put(NAME_COLUMN, user.name);
        values.put(NUMBER_TYPE_COLUMN, ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM);
        values.put(LABEL_COLUMN, (String)null);
        values.put(NUMBER_COLUMN, user.numbers.get(0).number);
        values.put(TYPE_COLUMN, PUSH_TYPE);
        mDatabase.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_IGNORE);
      }
      Log.d(TAG, "finished populating push users.");
    }
  }

  private static class TypedCursorWrapper extends CursorWrapper {

    private final int pushColumnIndex;

    public TypedCursorWrapper(Cursor cursor) {
      super(cursor);
      pushColumnIndex = cursor.getColumnCount();
    }

    @Override
    public int getColumnCount() {
      return super.getColumnCount() + 1;
    }

    @Override
    public int getColumnIndex(String columnName) {
      if (TYPE_COLUMN.equals(columnName)) return super.getColumnCount();
      else return super.getColumnIndex(columnName);
    }

    @Override
    public int getColumnIndexOrThrow(String columnName) throws IllegalArgumentException {
      if (TYPE_COLUMN.equals(columnName)) return super.getColumnCount();
      else return super.getColumnIndexOrThrow(columnName);
    }

    @Override
    public String getColumnName(int columnIndex) {
      if (columnIndex == pushColumnIndex) return TYPE_COLUMN;
      else                                return super.getColumnName(columnIndex);
    }

    @Override
    public String[] getColumnNames() {
      final String[] columns = new String[super.getColumnCount() + 1];
      System.arraycopy(super.getColumnNames(), 0, columns, 0, super.getColumnCount());
      columns[pushColumnIndex] = TYPE_COLUMN;
      return columns;
    }

    @Override
    public int getInt(int columnIndex) {
      if (columnIndex == pushColumnIndex) return NORMAL_TYPE;
      else                                return super.getInt(columnIndex);
    }
  }
}
