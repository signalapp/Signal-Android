package org.thoughtcrime.securesms.util.paging;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;

import java.util.List;

public class SizeFixResult<T> {

  private static final String TAG = Log.tag(SizeFixResult.class);

  final List<T> items;
  final int     total;

  private SizeFixResult(@NonNull List<T> items, int total) {
    this.items = items;
    this.total = total;
  }

  public List<T> getItems() {
    return items;
  }

  public int getTotal() {
    return total;
  }

  public static @NonNull <T> SizeFixResult<T> ensureMultipleOfPageSize(@NonNull List<T> records,
                                                                       int startPosition,
                                                                       int pageSize,
                                                                       int total)
  {
    if (records.size() + startPosition == total || (records.size() != 0 && records.size() % pageSize == 0)) {
      return new SizeFixResult<>(records, total);
    }

    if (records.size() < pageSize) {
      Log.w(TAG, "Hit a miscalculation where we don't have the full dataset, but it's smaller than a page size. records: " + records.size() + ", startPosition: " + startPosition + ", pageSize: " + pageSize + ", total: " + total);
      return new SizeFixResult<>(records, records.size() + startPosition);
    }

    Log.w(TAG, "Hit a miscalculation where our data size isn't a multiple of the page size. records: " + records.size() + ", startPosition: " + startPosition + ", pageSize: " + pageSize + ", total: " + total);
    int overflow = records.size() % pageSize;

    return new SizeFixResult<>(records.subList(0, records.size() - overflow), total);
  }
}
