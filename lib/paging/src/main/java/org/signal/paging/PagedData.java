package org.signal.paging;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import java.util.List;

import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.Subject;

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

  public PagingController<Key> getController() {
    return controller;
  }
}
