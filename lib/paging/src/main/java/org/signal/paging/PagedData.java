package org.signal.paging;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import java.util.List;

import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.Subject;
import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlow;

/**
 * The primary entry point for creating paged data.
 */
public class PagedData<Key> {

  private final PagingController<Key> controller;

  protected PagedData(PagingController<Key> controller) {
    this.controller = controller;
  }

  @AnyThread
  public static <Key, Data> LivePagedData<Key, Data> createForLiveData(@NonNull PagedDataSource<Key, Data> dataSource, @NonNull PagingConfig config) {
    MutableLiveData<List<Data>> liveData   = new MutableLiveData<>();
    PagingController<Key>       controller = new BufferedPagingController<>(dataSource, config, liveData::postValue);

    return new LivePagedData<>(liveData, controller);
  }

  @AnyThread
  public static <Key, Data> ObservablePagedData<Key, Data> createForObservable(@NonNull PagedDataSource<Key, Data> dataSource, @NonNull PagingConfig config) {
    Subject<List<Data>>   subject    = BehaviorSubject.create();
    PagingController<Key> controller = new BufferedPagingController<>(dataSource, config, subject::onNext);

    return new ObservablePagedData<>(subject, controller);
  }

  @AnyThread
  public static <Key, Data> StateFlowPagedData<Key, Data> createForStateFlow(@NonNull PagedDataSource<Key, Data> dataSource, @NonNull PagingConfig config) {
    return createForStateFlow(dataSource, config, java.util.Collections.emptyList());
  }

  /**
   * Same as {@link #createForStateFlow(PagedDataSource, PagingConfig)}, but seeds the backing
   * {@link StateFlow} with {@code initialValue} instead of an empty list. Useful when rebuilding a
   * source (e.g. on a query change) and you want the previously-loaded items to remain visible until
   * the new source's first page arrives, rather than briefly collapsing the list to empty.
   */
  @AnyThread
  public static <Key, Data> StateFlowPagedData<Key, Data> createForStateFlow(@NonNull PagedDataSource<Key, Data> dataSource, @NonNull PagingConfig config, @NonNull List<Data> initialValue) {
    MutableStateFlow<List<Data>> stateFlow  = kotlinx.coroutines.flow.StateFlowKt.MutableStateFlow(initialValue);
    PagingController<Key>        controller = new BufferedPagingController<>(dataSource, config, stateFlow::setValue);

    return new StateFlowPagedData<>(stateFlow, controller);
  }

  public PagingController<Key> getController() {
    return controller;
  }
}
