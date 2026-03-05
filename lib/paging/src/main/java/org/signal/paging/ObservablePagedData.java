package org.signal.paging;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;

import java.util.List;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.Subject;

/**
 * An implementation of {@link PagedData} that will provide data as an {@link Observable}.
 */
public class ObservablePagedData<Key, Data> extends PagedData<Key> {

  private final Observable<List<Data>> data;

  ObservablePagedData(@NonNull Observable<List<Data>> data, @NonNull PagingController<Key> controller) {
    super(controller);
    this.data = data;
  }

  @AnyThread
  public @NonNull Observable<List<Data>> getData() {
    return data;
  }
}
