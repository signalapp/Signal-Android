package org.thoughtcrime.securesms.util;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

public class ListenableFutureTask<V> extends FutureTask<V> {

  private FutureTaskListener<V> listener;

  public ListenableFutureTask(Callable<V> callable, FutureTaskListener<V> listener) {
    super(callable);
    this.listener = listener;
  }

  public synchronized void setListener(FutureTaskListener<V> listener) {
    this.listener = listener;
    if (this.isDone()) {
      callback();
    }
  }

  @Override
  protected synchronized void done() {
    callback();
  }

  private void callback() {
    if (this.listener != null) {
      try {
        this.listener.onSuccess(get());
      } catch (ExecutionException ee) {
        this.listener.onFailure(ee);
      } catch (InterruptedException e) {
        throw new AssertionError(e);
      }
    }
  }
}
