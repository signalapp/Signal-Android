package org.signal.paging;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * The workhorse of managing page requests.
 *
 * A controller whose life focuses around one invalidation cycle of a data set, and therefore has
 * a fixed size throughout. It assumes that all interface methods are called on a single thread,
 * which allows it to keep track of pending requests in a thread-safe way, while spinning off
 * tasks to fetch data on its own executor.
 */
class FixedSizePagingController<Key, Data> implements PagingController<Key> {

  private static final String TAG = FixedSizePagingController.class.getSimpleName();

  private static final Executor FETCH_EXECUTOR = SignalExecutors.newCachedSingleThreadExecutor("signal-FixedSizePagingController");
  private static final boolean  DEBUG          = false;

  private final PagedDataSource<Key, Data>  dataSource;
  private final PagingConfig                config;
  private final MutableLiveData<List<Data>> liveData;
  private final DataStatus                  loadState;
  private final Map<Key, Integer>           keyToPosition;

  private List<Data> data;

  private volatile boolean invalidated;

  FixedSizePagingController(@NonNull PagedDataSource<Key, Data> dataSource,
                            @NonNull PagingConfig config,
                            @NonNull MutableLiveData<List<Data>> liveData,
                            int size)
  {
    this.dataSource    = dataSource;
    this.config        = config;
    this.liveData      = liveData;
    this.loadState     = DataStatus.obtain(size);
    this.data          = new CompressedList<>(loadState.size());
    this.keyToPosition = new HashMap<>();
  }

  /**
   * We assume this method is always called on the same thread, so we can read our
   * {@code loadState} and construct the parameters of a fetch request. That fetch request can
   * then be performed on separate single-thread executor.
   */
  @Override
  public void onDataNeededAroundIndex(int aroundIndex) {
    if (invalidated) {
      Log.w(TAG, buildLog(aroundIndex, "Invalidated! At very beginning."));
      return;
    }

    if (loadState.size() == 0) {
      liveData.postValue(Collections.emptyList());
      return;
    }

    int leftPageBoundary  = (aroundIndex / config.pageSize()) * config.pageSize();
    int rightPageBoundary = leftPageBoundary + config.pageSize();
    int buffer            = config.bufferPages() * config.pageSize();

    int leftLoadBoundary  = Math.max(0, leftPageBoundary - buffer);
    int rightLoadBoundary = Math.min(loadState.size(), rightPageBoundary + buffer);

    int loadStart = loadState.getEarliestUnmarkedIndexInRange(leftLoadBoundary, rightLoadBoundary);

    if (loadStart < 0) {
      if (DEBUG) Log.i(TAG, buildLog(aroundIndex, "loadStart < 0"));
      return;
    }

    int loadEnd = loadState.getLatestUnmarkedIndexInRange(Math.max(leftLoadBoundary, loadStart), rightLoadBoundary) + 1;

    if (loadEnd <= loadStart) {
      if (DEBUG) Log.i(TAG, buildLog(aroundIndex, "loadEnd <= loadStart, loadEnd: " + loadEnd + ", loadStart: " + loadStart));
      return;
    }

    int totalSize = loadState.size();

    loadState.markRange(loadStart, loadEnd);

    if (DEBUG) Log.i(TAG, buildLog(aroundIndex, "start: " + loadStart + ", end: " + loadEnd + ", totalSize: " + totalSize));

    FETCH_EXECUTOR.execute(() -> {
      if (invalidated) {
        Log.w(TAG, buildLog(aroundIndex, "Invalidated! At beginning of load task."));
        return;
      }

      List<Data> loaded = dataSource.load(loadStart, loadEnd - loadStart, () -> invalidated);

      if (invalidated) {
        Log.w(TAG, buildLog(aroundIndex, "Invalidated! Just after data was loaded."));
        return;
      }

      List<Data> updated = new CompressedList<>(data);

      for (int i = 0, len = Math.min(loaded.size(), data.size() - loadStart); i < len; i++) {
        int  position = loadStart + i;
        Data item     = loaded.get(i);

        updated.set(position, item);
        keyToPosition.put(dataSource.getKey(item), position);
      }

      data = updated;
      liveData.postValue(updated);
    });
  }

  @Override
  public void onDataInvalidated() {
    if (invalidated) {
      return;
    }

    invalidated = true;
    loadState.recycle();
  }

  @Override
  public void onDataItemChanged(Key key) {
    FETCH_EXECUTOR.execute(() -> {
      Integer position = keyToPosition.get(key);

      if (position == null) {
        Log.w(TAG, "Notified of key " + key + " but it wasn't in the cache!");
        return;
      }

      if (invalidated) {
        Log.w(TAG, "Invalidated! Just before individual change was loaded for position " + position);
        return;
      }

      Data item = dataSource.load(key);

      if (item == null) {
        Log.w(TAG, "Notified of key " + key + " but the loaded item was null!");
        return;
      }

      if (invalidated) {
        Log.w(TAG, "Invalidated! Just after individual change was loaded for position " + position);
        return;
      }

      List<Data> updatedList = new CompressedList<>(data);

      updatedList.set(position, item);
      data = updatedList;
      liveData.postValue(updatedList);
    });
  }

  @Override
  public void onDataItemInserted(Key key, int position) {
    FETCH_EXECUTOR.execute(() -> {
      if (keyToPosition.containsKey(key)) {
        Log.w(TAG, "Notified of key " + key + " being inserted at " + position + ", but the item already exists!");
        return;
      }

      if (invalidated) {
        Log.w(TAG, "Invalidated! Just before individual insert was loaded for position " + position);
        return;
      }

      Data item = dataSource.load(key);

      if (item == null) {
        Log.w(TAG, "Notified of key " + key + " being inserted at " + position + ", but the loaded item was null!");
        return;
      }

      if (invalidated) {
        Log.w(TAG, "Invalidated! Just after individual insert was loaded for position " + position);
        return;
      }

      List<Data> updatedList = new CompressedList<>(data);

      updatedList.add(position, item);
      keyToPosition.put(dataSource.getKey(item), position);

      data = updatedList;
      liveData.postValue(updatedList);
    });
  }

  private static String buildLog(int aroundIndex, String message) {
    return "onDataNeededAroundIndex(" + aroundIndex + ") " + message;
  }
}
