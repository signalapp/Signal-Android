package org.whispersystems.signalservice.internal.util.concurrent;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class SettableFuture<T> implements ListenableFuture<T> {

  private final List<Listener<T>> listeners = new LinkedList<>();

  private          boolean   completed;
  private          boolean   canceled;
  private volatile T         result;
  private volatile Throwable exception;

  @Override
  public synchronized boolean cancel(boolean mayInterruptIfRunning) {
    if (!completed && !canceled) {
      canceled = true;
      return true;
    }

    return false;
  }

  @Override
  public synchronized boolean isCancelled() {
    return canceled;
  }

  @Override
  public synchronized boolean isDone() {
    return completed;
  }

  public boolean set(T result) {
    synchronized (this) {
      if (completed || canceled) return false;

      this.result    = result;
      this.completed = true;

      notifyAll();
    }

    notifyAllListeners();
    return true;
  }

  public boolean setException(Throwable throwable) {
    synchronized (this) {
      if (completed || canceled) return false;

      this.exception = throwable;
      this.completed = true;

      notifyAll();
    }

    notifyAllListeners();
    return true;
  }

  @Override
  public synchronized T get() throws InterruptedException, ExecutionException {
    while (!completed) wait();

    if (exception != null) throw new ExecutionException(exception);
    else                   return result;
  }

  @Override
  public synchronized T get(long timeout, TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException
  {
    long startTime = System.currentTimeMillis();

    while (!completed && System.currentTimeMillis() - startTime < unit.toMillis(timeout)) {
      wait(unit.toMillis(timeout));
    }

    if (!completed) throw new TimeoutException();
    else            return get();
  }

  @Override
  public void addListener(Listener<T> listener) {
    synchronized (this) {
      listeners.add(listener);

      if (!completed) return;
    }

    notifyListener(listener);
  }

  private void notifyAllListeners() {
    List<Listener<T>> localListeners;

    synchronized (this) {
      localListeners = new LinkedList<>(listeners);
    }

    for (Listener<T> listener : localListeners) {
      notifyListener(listener);
    }
  }

  private void notifyListener(Listener<T> listener) {
    if (exception != null) listener.onFailure(new ExecutionException(exception));
    else                   listener.onSuccess(result);
  }
}
