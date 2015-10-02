/**
 * Copyright (C) 2015 Open Whisper Systems
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
package org.thoughtcrime.securesms.database;

import android.content.Context;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;

/**
 * RecyclerView.Adapter that manages a Cursor, comparable to the CursorAdapter usable in ListView/GridView.
 */
public abstract class CursorRecyclerViewAdapter<VH extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<VH> {
  private final Context         context;
  private final DataSetObserver observer = new AdapterDataSetObserver();

  private Cursor  cursor;
  private boolean valid;

  protected CursorRecyclerViewAdapter(Context context, Cursor cursor) {
    this.context = context;
    this.cursor = cursor;
    if (cursor != null) {
      valid = true;
      cursor.registerDataSetObserver(observer);
    }

    setHasStableIds(false);
  }

  public Context getContext() {
    return context;
  }

  public Cursor getCursor() {
    return cursor;
  }

  public void changeCursor(Cursor cursor) {
    Cursor old = swapCursor(cursor);
    if (old != null) {
      old.close();
    }
  }

  public Cursor swapCursor(Cursor newCursor) {
    if (newCursor == cursor) {
      return null;
    }

    final Cursor oldCursor = cursor;
    if (oldCursor != null) {
      oldCursor.unregisterDataSetObserver(observer);
    }

    cursor = newCursor;
    if (cursor != null) {
      cursor.registerDataSetObserver(observer);
    }

    valid = cursor != null;
    notifyDataSetChanged();
    return oldCursor;
  }


  @Override
  public int getItemCount() {
    return isActiveCursor() ? cursor.getCount() : 0;
  }

  @Override
  public long getItemId(int position) {
    return isActiveCursor() && cursor.moveToPosition(position)
           ? cursor.getLong(cursor.getColumnIndexOrThrow("_id"))
           : 0;
  }

  public abstract void onBindViewHolder(VH viewHolder, @NonNull Cursor cursor);

  @Override
  public void onBindViewHolder(VH viewHolder, int position) {
    moveToPositionOrThrow(position);
    onBindViewHolder(viewHolder, cursor);
  }

  @Override public int getItemViewType(int position) {
    moveToPositionOrThrow(position);
    return getItemViewType(cursor);
  }

  public int getItemViewType(@NonNull Cursor cursor) {
    return 0;
  }

  private void assertActiveCursor() {
    if (!isActiveCursor()) {
      throw new IllegalStateException("this should only be called when the cursor is valid");
    }
  }

  private void moveToPositionOrThrow(final int position) {
    assertActiveCursor();
    if (!cursor.moveToPosition(position)) {
      throw new IllegalStateException("couldn't move cursor to position " + position);
    }
  }

  private boolean isActiveCursor() {
    return valid && cursor != null;
  }

  private class AdapterDataSetObserver extends DataSetObserver {
    @Override
    public void onChanged() {
      super.onChanged();
      valid = true;
      notifyDataSetChanged();
    }

    @Override
    public void onInvalidated() {
      super.onInvalidated();
      valid = false;
      notifyDataSetChanged();
    }
  }
}
