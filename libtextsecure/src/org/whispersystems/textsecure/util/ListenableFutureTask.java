package org.whispersystems.textsecure.util;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

public class ListenableFutureTask<V> extends FutureTask<V> {

  private final List<FutureTaskListener<V>> listeners;

  public ListenableFutureTask(Callable<V> callable) {
    super(callable);
    this.listeners = new LinkedList<>();
  }

  public synchronized void addListener(FutureTaskListener<V> listener) {
    if (this.isDone()) {
      callback(listener);
      return;
    }
    listeners.add(listener);
  }

  public synchronized boolean removeListener(FutureTaskListener<V> listener) {
    return listeners.remove(listener);
  }

  @Override
  protected synchronized void done() {
    callback();
  }

  private void callback() {
    for (FutureTaskListener<V> listener : listeners) {
      callback(listener);
    }
  }

  private void callback(FutureTaskListener<V> listener) {
    if (listener != null) {
      try {
        listener.onSuccess(get());
      } catch (ExecutionException ee) {
        listener.onFailure(ee);
      } catch (InterruptedException e) {
        throw new AssertionError(e);
      }
    }
  }
}
