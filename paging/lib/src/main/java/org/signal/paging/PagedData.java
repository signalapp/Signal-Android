package org.signal.paging;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.List;

/**
 * The primary entry point for creating paged data.
 */
public final class PagedData<Key, Data> {

  private final LiveData<List<Data>>  data;
  private final PagingController<Key> controller;

  @AnyThread
  public static <Key, Data> PagedData<Key, Data> create(@NonNull PagedDataSource<Key, Data> dataSource, @NonNull PagingConfig config) {
    MutableLiveData<List<Data>> liveData   = new MutableLiveData<>();
    PagingController<Key>       controller = new BufferedPagingController<>(dataSource, config, liveData);

    return new PagedData<>(liveData, controller);
  }

  private PagedData(@NonNull LiveData<List<Data>> data, @NonNull PagingController<Key> controller) {
    this.data       = data;
    this.controller = controller;
  }

  @AnyThread
  public @NonNull LiveData<List<Data>> getData() {
    return data;
  }

  @AnyThread
  public @NonNull PagingController<Key> getController() {
    return controller;
  }
}
