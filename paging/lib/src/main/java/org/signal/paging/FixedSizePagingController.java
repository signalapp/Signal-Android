package org.signal.paging;

import androidx.annotation.NonNull;

import org.signal.core.util.ThreadUtil;
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

  private static final String TAG = Log.tag(FixedSizePagingController.class);

  private static final Executor FETCH_EXECUTOR = SignalExecutors.newCachedSingleThreadExecutor("signal-FixedSizePagingController", ThreadUtil.PRIORITY_UI_BLOCKING_THREAD);
  private static final boolean  DEBUG          = false;

  private final PagedDataSource<Key, Data>  dataSource;
  private final PagingConfig                config;
  private final DataStream<Data>            dataStream;
  private final DataStatus                  loadState;
  private final Map<Key, Integer>           keyToPosition;

  private List<Data> data;

  private volatile boolean invalidated;

  FixedSizePagingController(@NonNull PagedDataSource<Key, Data> dataSource,
                            @NonNull PagingConfig config,
                            @NonNull DataStream<Data> dataStream,
                            int size)
  {
    this.dataSource    = dataSource;
    this.config        = config;
    this.dataStream    = dataStream;
    this.loadState     = DataStatus.obtain(size);
    this.data          = new CompressedList<>(loadState.size());
    this.keyToPosition = new HashMap<>();

    if (DEBUG) Log.d(TAG, "[Constructor] Creating with size " + size + " (loadState.size() = " + loadState.size() + ")");
  }

  /**
   * We assume this method is always called on the same thread, so we can read our
   * {@code loadState} and construct the parameters of a fetch request. That fetch request can
   * then be performed on separate single-thread executor.
   */
  @Override
  public void onDataNeededAroundIndex(int aroundIndex) {
    if (invalidated) {
      Log.w(TAG, buildDataNeededLog(aroundIndex, "Invalidated! At very beginning."));
      return;
    }

    final int loadStart;
    final int loadEnd;

    synchronized (loadState) {
      if (loadState.size() == 0) {
        dataStream.next(Collections.emptyList());
        return;
      }

      int leftPageBoundary  = (aroundIndex / config.pageSize()) * config.pageSize();
      int rightPageBoundary = leftPageBoundary + config.pageSize();
      int buffer            = config.bufferPages() * config.pageSize();

      int leftLoadBoundary  = Math.max(0, leftPageBoundary - buffer);
      int rightLoadBoundary = Math.min(loadState.size(), rightPageBoundary + buffer);

      loadStart = loadState.getEarliestUnmarkedIndexInRange(leftLoadBoundary, rightLoadBoundary);

      if (loadStart < 0) {
        if (DEBUG) Log.i(TAG, buildDataNeededLog(aroundIndex, "loadStart < 0"));
        return;
      }

      loadEnd = loadState.getLatestUnmarkedIndexInRange(Math.max(leftLoadBoundary, loadStart), rightLoadBoundary) + 1;

      if (loadEnd <= loadStart) {
        if (DEBUG) Log.i(TAG, buildDataNeededLog(aroundIndex, "loadEnd <= loadStart, loadEnd: " + loadEnd + ", loadStart: " + loadStart));
        return;
      }

      int totalSize = loadState.size();

      loadState.markRange(loadStart, loadEnd);

      if (DEBUG) Log.i(TAG, buildDataNeededLog(aroundIndex, "start: " + loadStart + ", end: " + loadEnd + ", totalSize: " + totalSize));
    }

    FETCH_EXECUTOR.execute(() -> {
      if (invalidated) {
        Log.w(TAG, buildDataNeededLog(aroundIndex, "Invalidated! At beginning of load task."));
        return;
      }

      List<Data> loaded = dataSource.load(loadStart, loadEnd - loadStart, () -> invalidated);

      if (invalidated) {
        Log.w(TAG, buildDataNeededLog(aroundIndex, "Invalidated! Just after data was loaded."));
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
      dataStream.next(updated);
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
    if (DEBUG) Log.d(TAG, buildItemChangedLog(key, ""));

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

      synchronized (loadState) {
        loadState.mark(position);
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
      dataStream.next(updatedList);

      if (DEBUG) Log.d(TAG, buildItemChangedLog(key, "Published updated data"));
    });
  }

  @Override
  public void onDataItemInserted(Key key, int inputPosition) {
    if (DEBUG) Log.d(TAG, buildItemInsertedLog(key, inputPosition, ""));

    FETCH_EXECUTOR.execute(() -> {
      int position = inputPosition;
      if (position == POSITION_END) {
        position = data.size();
      }

      if (keyToPosition.containsKey(key)) {
        Log.w(TAG, "Notified of key " + key + " being inserted at " + position + ", but the item already exists!");
        return;
      }

      if (invalidated) {
        Log.w(TAG, "Invalidated! Just before individual insert was loaded for position " + position);
        return;
      }

      synchronized (loadState) {
        loadState.insertState(position, true);
        if (DEBUG) Log.d(TAG, buildItemInsertedLog(key, position, "Size of loadState updated to " + loadState.size()));
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
      rebuildKeyToPositionMap(keyToPosition, updatedList, dataSource);

      data = updatedList;
      dataStream.next(updatedList);

      if (DEBUG) Log.d(TAG, buildItemInsertedLog(key, position, "Published updated data"));
    });
  }

  private void rebuildKeyToPositionMap(@NonNull Map<Key, Integer> map, @NonNull List<Data> dataList, @NonNull PagedDataSource<Key, Data> dataSource) {
    map.clear();

    for (int i = 0, len = dataList.size(); i < len; i++) {
      Data item = dataList.get(i);
      if (item != null) {
        map.put(dataSource.getKey(item), i);
      }
    }
  }

  private String buildDataNeededLog(int aroundIndex, String message) {
    return "[onDataNeededAroundIndex(" + aroundIndex + "), size: " + loadState.size() + "] " + message;
  }

  private String buildItemInsertedLog(Key key, int position, String message) {
    return "[onDataItemInserted(" + key + ", " + position + "), size: " + loadState.size() + "] " + message;
  }

  private String buildItemChangedLog(Key key, String message) {
    return "[onDataItemChanged(" + key + "), size: " + loadState.size() + "] " + message;
  }
}
