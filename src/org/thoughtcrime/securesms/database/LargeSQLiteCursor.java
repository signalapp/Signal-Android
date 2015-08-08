package org.thoughtcrime.securesms.database;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.database.AbstractCursor;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build.VERSION_CODES;
import android.support.annotation.NonNull;
import android.support.v4.util.LruCache;
import android.util.Log;

import org.thoughtcrime.securesms.util.VisibleForTesting;

public class LargeSQLiteCursor extends AbstractCursor {
  private static final String TAG = LargeSQLiteCursor.class.getSimpleName();

  private final SQLiteDatabase db;
  private final String         query;
  private final int            windowSize;

  private int             count;
  private ContentObserver contentObserver;
  private ContentResolver contentResolver;
  private DataSetObserver dataSetObserver;
  private Uri             notificationUri;
  private CursorLruCache  loadedCursors;
  private Cursor          current;

  public LargeSQLiteCursor(SQLiteDatabase db, String query, int windowSize) {
    this.db            = db;
    this.query         = query;
    this.windowSize    = windowSize;
    this.count         = getCount(db, query);
    this.loadedCursors = new CursorLruCache(4);
    this.current       = getCursorFor(0);
  }

  private int getCount(SQLiteDatabase db, String query) {
    Cursor countCursor = null;
    try {
      long start = System.currentTimeMillis();
      countCursor = db.rawQuery("SELECT COUNT(1) FROM (" + query + ");", null);
      if (!countCursor.moveToFirst()) {
        throw new IllegalStateException("couldn't get count of results in query");
      }
      final int count = countCursor.getInt(0);
      Log.w(TAG, "got count " + count + " -> " + (System.currentTimeMillis() - start) + "ms");
      return count;
    } finally {
      if (countCursor != null) countCursor.close();
    }
  }

  private String buildWindowedQuery(final int limit, final int offset) {
    return query + " LIMIT " + limit + " OFFSET " + offset;
  }

  private Cursor getCursorFor(int position) {
    if (position < 0 || (count > 0 && position >= count)) return null;

    final int    offset       = position - (position % windowSize);
    final Cursor loadedCursor = loadedCursors.get(offset);

    if (loadedCursor != null) {
      loadedCursors.remove(offset);
      loadedCursors.put(offset, loadedCursor);
      return loadedCursor;
    } else {
      @SuppressLint("Recycle")
      Cursor cursor = db.rawQuery(buildWindowedQuery(windowSize, offset), null);
      if (notificationUri != null) cursor.setNotificationUri(contentResolver, notificationUri);
      if (dataSetObserver != null) cursor.registerDataSetObserver(dataSetObserver);
      if (contentObserver != null) cursor.registerContentObserver(contentObserver);
      loadedCursors.put(offset, cursor);
      Log.w(TAG, "loaded new cursor for offset " + offset + ", holding " + loadedCursors.size() + " open cursors.");
      return cursor;
    }
  }

  private boolean setPosition(int position) {
    current = getCursorFor(position);
    return current != null && current.moveToPosition(position % windowSize);
  }

  @Override public int getCount() {
    return count;
  }

  @Override public int getColumnIndex(@NonNull String columnName) {
    return current.getColumnIndex(columnName);
  }

  @Override public int getColumnIndexOrThrow(@NonNull String columnName) throws IllegalArgumentException {
    return current.getColumnIndexOrThrow(columnName);
  }

  @Override public String getColumnName(int columnIndex) {
    return current.getColumnName(columnIndex);
  }

  @Override public String[] getColumnNames() {
    return current.getColumnNames();
  }

  @Override public int getColumnCount() {
    return current.getColumnCount();
  }

  @Override public byte[] getBlob(int columnIndex) {
    return current.getBlob(columnIndex);
  }

  @Override public String getString(int columnIndex) {
    return current.getString(columnIndex);
  }

  @Override public short getShort(int columnIndex) {
    return current.getShort(columnIndex);
  }

  @Override public int getInt(int columnIndex) {
    return current.getInt(columnIndex);
  }

  @Override public long getLong(int columnIndex) {
    return current.getLong(columnIndex);
  }

  @Override public float getFloat(int columnIndex) {
    return current.getFloat(columnIndex);
  }

  @Override public double getDouble(int columnIndex) {
    return current.getDouble(columnIndex);
  }

  @TargetApi(VERSION_CODES.HONEYCOMB)
  @Override public int getType(int columnIndex) {
    return current.getType(columnIndex);
  }

  @Override public boolean isNull(int columnIndex) {
    return current.isNull(columnIndex);
  }

  @Override public void close() {
    current = null;
    loadedCursors.evictAll();
  }

  @Override public boolean onMove(int oldPosition, int newPosition) {
    return setPosition(newPosition);
  }

  @Override public void registerContentObserver(ContentObserver observer) {
    this.contentObserver = observer;
    for (Cursor cursor : loadedCursors.snapshot().values()) {
      cursor.registerContentObserver(observer);
    }
  }

  @Override public void unregisterContentObserver(ContentObserver observer) {
    this.contentObserver = null;
    for (Cursor cursor : loadedCursors.snapshot().values()) {
      cursor.unregisterContentObserver(observer);
    }
  }

  @Override public void registerDataSetObserver(DataSetObserver observer) {
    this.dataSetObserver = observer;
    for (Cursor cursor : loadedCursors.snapshot().values()) {
      cursor.registerDataSetObserver(observer);
    }
  }

  @Override public void unregisterDataSetObserver(DataSetObserver observer) {
    this.dataSetObserver = null;
    for (Cursor cursor : loadedCursors.snapshot().values()) {
      cursor.unregisterDataSetObserver(observer);
    }
  }

  @Override public void setNotificationUri(ContentResolver cr, Uri uri) {
    this.contentResolver = cr;
    this.notificationUri = uri;
    for (Cursor cursor : loadedCursors.snapshot().values()) {
      cursor.setNotificationUri(cr, uri);
    }
  }

  @TargetApi(VERSION_CODES.KITKAT)
  @Override public Uri getNotificationUri() {
    return notificationUri;
  }

  @VisibleForTesting class CursorLruCache extends LruCache<Integer, Cursor> {

    public CursorLruCache(int maxSize) {
      super(maxSize);
    }

    @Override protected void entryRemoved(boolean evicted, Integer key, Cursor oldc, Cursor newc) {
      if (oldc != current && evicted) {
        Log.w(TAG, "evicting cursor with offset " + key);
        oldc.close();
      }
    }
  }

  @VisibleForTesting CursorLruCache getLoadedCursors() {
    return loadedCursors;
  }
}
