package org.signal.paging;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.util.List;

/**
 * Represents a source of data that can be queried.
 */
public interface PagedDataSource<Key, Data> {
  /**
   * @return The total size of the data set.
   */
  @WorkerThread
  int size();

  /**
   * @param start              The index of the first item that should be included in your results.
   * @param length             The total number of items you should return.
   * @param totalSize          The total number of items in the data source
   * @param cancellationSignal An object that you can check to see if the load operation was canceled.
   * @return A list of length {@code length} that represents the data starting at {@code start}.
   * If you don't have the full range, just populate what you can.
   */
  @WorkerThread
  @NonNull List<Data> load(int start, int length, int totalSize, @NonNull CancellationSignal cancellationSignal);

  @WorkerThread
  @Nullable Data load(Key key);

  @WorkerThread
  @NonNull Key getKey(@NonNull Data data);

  interface CancellationSignal {
    /**
     * @return True if the operation has been canceled, otherwise false.
     */
    boolean isCanceled();
  }
}
