package org.thoughtcrime.securesms.database;


import android.content.Context;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public abstract class FastCursorRecyclerViewAdapter<VH extends RecyclerView.ViewHolder, T>
    extends CursorRecyclerViewAdapter<VH>
{
  private static final String TAG = FastCursorRecyclerViewAdapter.class.getSimpleName();

  private final LinkedList<T> fastRecords       = new LinkedList<>();
  private final List<Long>    releasedRecordIds = new LinkedList<>();

  protected FastCursorRecyclerViewAdapter(Context context, Cursor cursor) {
    super(context, cursor);
  }

  public void addFastRecord(@NonNull T record) {
    fastRecords.addFirst(record);
    notifyDataSetChanged();
  }

  public void releaseFastRecord(long id) {
    synchronized (releasedRecordIds) {
      releasedRecordIds.add(id);
    }
  }

  protected void cleanFastRecords() {
    synchronized (releasedRecordIds) {
      Iterator<Long> releaseIdIterator = releasedRecordIds.iterator();

      while (releaseIdIterator.hasNext()) {
        long        releasedId         = releaseIdIterator.next();
        Iterator<T> fastRecordIterator = fastRecords.iterator();

        while (fastRecordIterator.hasNext()) {
          if (isRecordForId(fastRecordIterator.next(), releasedId)) {
            fastRecordIterator.remove();
            releaseIdIterator.remove();
            break;
          }
        }
      }
    }
  }

  protected abstract T getRecordFromCursor(@NonNull Cursor cursor);
  protected abstract void onBindItemViewHolder(VH viewHolder, @NonNull T record);
  protected abstract long getItemId(@NonNull T record);
  protected abstract int getItemViewType(@NonNull T record);
  protected abstract boolean isRecordForId(@NonNull T record, long id);

  @Override
  public int getItemViewType(@NonNull Cursor cursor) {
    T record = getRecordFromCursor(cursor);
    return getItemViewType(record);
  }

  @Override
  public void onBindItemViewHolder(VH viewHolder, @NonNull Cursor cursor) {
    T record = getRecordFromCursor(cursor);
    onBindItemViewHolder(viewHolder, record);
  }

  @Override
  public void onBindFastAccessItemViewHolder(VH viewHolder, int position) {
    int calculatedPosition = getCalculatedPosition(position);
    onBindItemViewHolder(viewHolder, fastRecords.get(calculatedPosition));
  }

  @Override
  protected int getFastAccessSize() {
    return fastRecords.size();
  }

  protected T getRecordForPositionOrThrow(int position) {
    if (isFastAccessPosition(position)) {
      return fastRecords.get(getCalculatedPosition(position));
    } else {
      Cursor cursor = getCursorAtPositionOrThrow(position);
      return getRecordFromCursor(cursor);
    }
  }

  protected int getFastAccessItemViewType(int position) {
    return getItemViewType(fastRecords.get(getCalculatedPosition(position)));
  }

  protected boolean isFastAccessPosition(int position) {
    position = getCalculatedPosition(position);
    return position >= 0 && position < fastRecords.size();
  }

  protected long getFastAccessItemId(int position) {
    return getItemId(fastRecords.get(getCalculatedPosition(position)));
  }

  private int getCalculatedPosition(int position) {
    return hasHeaderView() ? position - 1 : position;
  }

}
