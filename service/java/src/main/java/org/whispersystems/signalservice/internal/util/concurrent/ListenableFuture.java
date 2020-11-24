package org.whispersystems.signalservice.internal.util.concurrent;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public interface ListenableFuture<T> extends Future<T> {
  void addListener(Listener<T> listener);

  public interface Listener<T> {
    public void onSuccess(T result);
    public void onFailure(ExecutionException e);
  }
}
