package org.thoughtcrime.securesms.util.concurrent;

import java.util.concurrent.ExecutionException;

public interface ListenableFuture<T> {
  void addListener(Listener<T> listener);

  public interface Listener<T> {
    public void onSuccess(T result);
    public void onFailure(ExecutionException e);
  }
}
