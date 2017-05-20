package org.thoughtcrime.securesms.util.concurrent;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public interface ListenableFuture<T> extends Future<T> {
  void addListener(Listener<T> listener);

  interface Listener<T> {
    void onSuccess(T result);
    void onFailure(ExecutionException e);
  }
}
