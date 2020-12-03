package org.signal.paging;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.List;

/**
 * The primary entry point for creating paged data.
 */
public final class PagedData<E> {

  private final LiveData<List<E>> data;
  private final PagingController  controller;

  @AnyThread
  public static <E> PagedData<E> create(@NonNull PagedDataSource<E> dataSource, @NonNull PagingConfig config) {
    MutableLiveData<List<E>> liveData   = new MutableLiveData<>();
    PagingController         controller = new BufferedPagingController<>(dataSource, config, liveData);

    return new PagedData<>(liveData, controller);
  }

  private PagedData(@NonNull LiveData<List<E>> data, @NonNull PagingController controller) {
    this.data       = data;
    this.controller = controller;
  }

  @AnyThread
  public @NonNull LiveData<List<E>> getData() {
    return data;
  }

  @AnyThread
  public @NonNull PagingController getController() {
    return controller;
  }
}
