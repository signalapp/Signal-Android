package org.signal.paging;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * We have a bit of a threading problem -- we want our controller to have a fixed size so that it
 * can keep track of which ranges of requests are in flight, but it needs to make a blocking call
 * to find out the size of the dataset first!
 *
 * So what this controller does is use a serial executor so that it can buffer calls to a secondary
 * controller. The first task on the executor creates the first controller, so all future calls to
 * {@link #onDataNeededAroundIndex(int)} are guaranteed to have an active controller.
 *
 * It's also worth noting that this controller has lifecycle that matches the {@link PagedData} that
 * contains it. When invalidations come in, this class will just swap out the active controller with
 * a new one.
 */
class BufferedPagingController<Key, Data> implements PagingController<Key> {

  private final PagedDataSource<Key, Data>  dataSource;
  private final PagingConfig                config;
  private final DataStream<Data>            dataStream;
  private final Executor                    serializationExecutor;

  private PagingController<Key> activeController;
  private int                   lastRequestedIndex;

  BufferedPagingController(@NonNull PagedDataSource<Key, Data> dataSource,
                           @NonNull PagingConfig config,
                           @NonNull DataStream<Data> dataStream)
  {
    this.dataSource            = dataSource;
    this.config                = config;
    this.dataStream            = dataStream;
    this.serializationExecutor = Executors.newSingleThreadExecutor();

    this.activeController   = null;
    this.lastRequestedIndex = config.startIndex();

    onDataInvalidated();
  }

  @Override
  public void onDataNeededAroundIndex(int aroundIndex) {
    serializationExecutor.execute(() -> {
      lastRequestedIndex = aroundIndex;
      activeController.onDataNeededAroundIndex(aroundIndex);
    });
  }

  @Override
  public void onDataInvalidated() {
    serializationExecutor.execute(() -> {
      if (activeController != null) {
        activeController.onDataInvalidated();
      }

      activeController = new FixedSizePagingController<>(dataSource, config, dataStream, dataSource.size());
      activeController.onDataNeededAroundIndex(lastRequestedIndex);
    });
  }

  @Override
  public void onDataItemChanged(Key key) {
    serializationExecutor.execute(() -> {
      if (activeController != null) {
        activeController.onDataItemChanged(key);
      }
    });
  }

  @Override
  public void onDataItemInserted(Key key, int position) {
    serializationExecutor.execute(() -> {
      if (activeController != null) {
        activeController.onDataItemInserted(key, position);
      }
    });
  }
}
