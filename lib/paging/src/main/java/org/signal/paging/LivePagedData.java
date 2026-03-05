package org.signal.paging;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import java.util.List;

/**
 * An implementation of {@link PagedData} that will provide data as a {@link LiveData}.
 */
public class LivePagedData<Key, Data> extends PagedData<Key> {

  private final LiveData<List<Data>>  data;

  LivePagedData(@NonNull LiveData<List<Data>> data, @NonNull PagingController<Key> controller) {
    super(controller);
    this.data = data;
  }

  @AnyThread
  public @NonNull LiveData<List<Data>> getData() {
    return data;
  }
}
