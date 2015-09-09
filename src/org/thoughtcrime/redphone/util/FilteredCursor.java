/*
 * Copyright (C) 2014 Clover Network, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.thoughtcrime.redphone.util;

import android.database.CharArrayBuffer;
import android.database.Cursor;
import android.database.CursorWrapper;

/**
 * Wraps a Cursor and allowing its positions to be filtered out, repeated, or reordered. Common ways of creating
 * FilteredCursor objects are provided by the {@link FilteredCursorFactory}.
 *
 * Note that if the source Cursor exceeds the size of the {@link android.database.CursorWindow} the FilteredCursor
 * may end up having extremely poor performance due to frequent CursorWindow cache misses. In those cases it is
 * recommended that source Cursor contain less data.
 *
 * @author Jacob Whitaker Abrams
 */
public class FilteredCursor extends CursorWrapper {

  // Globally map master Cursor to FilteredCursors, when all FilteredCursors are closed go ahead and close the master
  // This would need to go into a singleton if other classes similar to FilteredCursor exist
//  private static final Map<Cursor, Set<FilteredCursor>> sMasterCursorMap =
//      Collections.synchronizedMap(new WeakHashMap<Cursor, Set<FilteredCursor>>());

  private int[] mFilterMap;
  private int mPos = -1;
  private final Cursor mCursor;
  private boolean mClosed;

//  /**
//   * Create a FilteredCursor that appears identical to its wrapped Cursor.
//   */
//  public static FilteredCursor createUsingIdentityFilter(Cursor cursor) {
//    if (cursor == null) {
//      return null;
//    }
//    return new FilteredCursor(cursor);
//  }

  /**
   * Create a new FilteredCursor using the given filter. The filter specifies where rows of the given Cursor should
   * appear in the FilteredCursor. For example if filter = { 5, 9 } then the FilteredCursor will have two rows, the
   * first row maps to row 5 in the source Cursor and the second row maps to row 9 in the source cursor. Returns null
   * if the provided cursor is null. A value of -1 in the filter is treated as an empty row in the Cursor with no data,
   * see {@link FilteredCursor#isPositionEmpty()}.
   */
  public static FilteredCursor createUsingFilter(Cursor cursor, int[] filter) {
    if (cursor == null) {
      return null;
    }
    if (filter == null) {
      throw new NullPointerException();
    }
    return new FilteredCursor(cursor, filter);
  }

//  private FilteredCursor(Cursor cursor) {
//    this(cursor, null);
//    resetToIdentityFilter();
//  }

  private FilteredCursor(Cursor cursor, int[] filterMap) {
    super(cursor);
    mCursor = cursor;
    mFilterMap = filterMap;
//    attachToMasterCursor();
  }

  public int[] getFilterMap() {
    return mFilterMap;
  }

//  /**
//   * Reset the filter so it appears identical to its wrapped Cursor.
//   */
//  public FilteredCursor resetToIdentityFilter() {
//    int count = mCursor.getCount();
//    int[] filterMap = new int[count];
//
//    for (int i = 0; i < count; i++) {
//      filterMap[i] = i;
//    }
//
//    mFilterMap = filterMap;
//    mPos = -1;
//    return this;
//  }

//  /**
//   * Returns true if the FilteredCursor appears identical to its wrapped Cursor.
//   */
//  public boolean isIdentityFilter() {
//    int count = mCursor.getCount();
//    if (mFilterMap.length != count) {
//      return false;
//    }
//
//    for (int i = 0; i < count; i++) {
//      if (mFilterMap[i] != i) {
//        return false;
//      }
//    }
//
//    return true;
//  }

//  /**
//   * Rearrange the filter. The new arrangement is based on the current filter arrangement, not on the source Cursor's
//   * arrangement.
//   */
//  public FilteredCursor refilter(int[] newArrangement) {
//    final int newMapSize = newArrangement.length;
//    int[] newMap = new int[newMapSize];
//    for (int i = 0; i < newMapSize; i++) {
//      newMap[i] = mFilterMap[newArrangement[i]];
//    }
//
//    mFilterMap = newMap;
//    mPos = -1;
//    return this;
//  }

  /**
   * True if the current cursor position has no data. Attempting to access data in an empty row with any of the getters
   * will throw {@link UnsupportedOperationException}.
   */
  public boolean isPositionEmpty() {
    return mFilterMap[mPos] == -1;
  }

  private void throwIfEmptyRow() {
    if (isPositionEmpty()) {
      throw new UnsupportedOperationException("Cannot access data in an empty row");
    }
  }

//  public void swapItems(int itemOne, int itemTwo) {
//    int temp = mFilterMap[itemOne];
//    mFilterMap[itemOne] = mFilterMap[itemTwo];
//    mFilterMap[itemTwo] = temp;
//  }

  @Override
  public int getCount() {
    return mFilterMap.length;
  }

  @Override
  public int getPosition() {
    return mPos;
  }

  @Override
  public boolean moveToPosition(int position) {
    // Make sure position isn't past the end of the cursor
    final int count = getCount();
    if (position >= count) {
      mPos = count;
      return false;
    }

    // Make sure position isn't before the beginning of the cursor
    if (position < 0) {
      mPos = -1;
      return false;
    }

    final int realPosition = mFilterMap[position];

    // When moving to an empty position, just pretend we did it
    boolean moved = realPosition == -1 ? true : super.moveToPosition(realPosition);
    if (moved) {
      mPos = position;
    } else {
      mPos = -1;
    }
    return moved;
  }

  @Override
  public final boolean move(int offset) {
    return moveToPosition(mPos + offset);
  }

  @Override
  public final boolean moveToFirst() {
    return moveToPosition(0);
  }

  @Override
  public final boolean moveToLast() {
    return moveToPosition(getCount() - 1);
  }

  @Override
  public final boolean moveToNext() {
    return moveToPosition(mPos + 1);
  }

  @Override
  public final boolean moveToPrevious() {
    return moveToPosition(mPos - 1);
  }

  @Override
  public final boolean isFirst() {
    return mPos == 0 && getCount() != 0;
  }

  @Override
  public final boolean isLast() {
    int count = getCount();
    return mPos == (count - 1) && count != 0;
  }

  @Override
  public final boolean isBeforeFirst() {
    if (getCount() == 0) {
      return true;
    }
    return mPos == -1;
  }

  @Override
  public final boolean isAfterLast() {
    if (getCount() == 0) {
      return true;
    }
    return mPos == getCount();
  }

  @Override
  public boolean isNull(int columnIndex) {
    throwIfEmptyRow();
    return mCursor.isNull(columnIndex);
  }

  @Override
  public void copyStringToBuffer(int columnIndex, CharArrayBuffer buffer) {
    throwIfEmptyRow();
    mCursor.copyStringToBuffer(columnIndex, buffer);
  }

  @Override
  public byte[] getBlob(int columnIndex) {
    throwIfEmptyRow();
    return mCursor.getBlob(columnIndex);
  }

  @Override
  public double getDouble(int columnIndex) {
    throwIfEmptyRow();
    return mCursor.getDouble(columnIndex);
  }

  @Override
  public float getFloat(int columnIndex) {
    throwIfEmptyRow();
    return mCursor.getFloat(columnIndex);
  }

  @Override
  public int getInt(int columnIndex) {
    throwIfEmptyRow();
    return mCursor.getInt(columnIndex);
  }

  @Override
  public long getLong(int columnIndex) {
    throwIfEmptyRow();
    return mCursor.getLong(columnIndex);
  }

  @Override
  public short getShort(int columnIndex) {
    throwIfEmptyRow();
    return mCursor.getShort(columnIndex);
  }

  @Override
  public String getString(int columnIndex) {
    throwIfEmptyRow();
    return mCursor.getString(columnIndex);
  }

  @Override
  public boolean isClosed() {
    return mClosed || getMasterCursor().isClosed();
  }

  @Override
  public void close() {
    // Mark this Cursor as closed
    mClosed = true;

    // Find the master Cursor and close it if all linked cursors are closed
    Cursor masterCursor = getMasterCursor();

//    Set<FilteredCursor> linkedFilteredCursorSet = sMasterCursorMap.get(masterCursor);
//    if (linkedFilteredCursorSet == null) {
      masterCursor.close(); // Shouldn't ever happen?
//    } else {
//      linkedFilteredCursorSet.remove(this);
//      if (linkedFilteredCursorSet.isEmpty()) {
//        masterCursor.close();
//      }
//    }

//    if (masterCursor.isClosed()) {
//      sMasterCursorMap.remove(masterCursor);
//    }
  }

  @Override
  @Deprecated
  public boolean requery() {
    throw new UnsupportedOperationException();
  }

//  private void attachToMasterCursor() {
//    Cursor masterCursor = getMasterCursor();
//    Set<FilteredCursor> filteredCursorSet = sMasterCursorMap.get(masterCursor);
//    if (filteredCursorSet == null) {
//      filteredCursorSet = Collections.synchronizedSet(new HashSet<FilteredCursor>());
//      sMasterCursorMap.put(masterCursor, filteredCursorSet);
//    }
//    filteredCursorSet.add(this);
//  }

//  /** Returns the first non-CursorWrapper instance contained within this object. */
//  public Cursor getMasterCursor() {
//    Cursor cursor = mCursor;
//
//    while (cursor instanceof CursorWrapper) {
//      cursor = ((CursorWrapper) cursor).getWrappedCursor();
//    }
//
//    return cursor;
//  }

  public Cursor getMasterCursor() {
    return mCursor;
  }

//  /** Returns the first FilteredCursor wrapped by the provided cursor or null if no FilteredCursor is found. */
//  public static FilteredCursor unwrapFilteredCursor(Cursor cursor) {
//    while (cursor instanceof CursorWrapper) {
//      if (cursor instanceof FilteredCursor) {
//        return (FilteredCursor)cursor;
//      } else {
//        cursor = ((CursorWrapper) cursor).getWrappedCursor();
//      }
//    }
//
//    return null;
//  }

}