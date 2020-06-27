package org.whispersystems.signalservice.internal.util.concurrent;

import org.whispersystems.libsignal.logging.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A future that allows you to have multiple ways to compute a result. If one fails, the calculation
 * will fall back to the next in the list.
 *
 * You will only see a failure if the last attempt in the list fails.
 */
public final class CascadingFuture<T> implements ListenableFuture<T> {

  private static final String TAG = CascadingFuture.class.getSimpleName();

  private SettableFuture<T> result;

  public CascadingFuture(List<Callable<ListenableFuture<T>>> callables, ExceptionChecker exceptionChecker) {
    if (callables.isEmpty()) {
      throw new IllegalArgumentException("Must have at least one callable!");
    }

    this.result = new SettableFuture<>();

    doNext(new ArrayList<>(callables), exceptionChecker);
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    return result.cancel(mayInterruptIfRunning);
  }

  @Override
  public boolean isCancelled() {
    return result.isCancelled();
  }

  @Override
  public boolean isDone() {
    return result.isDone();
  }

  @Override
  public T get() throws ExecutionException, InterruptedException {
    return result.get();
  }

  @Override
  public T get(long timeout, TimeUnit unit) throws ExecutionException, InterruptedException, TimeoutException {
    return result.get(timeout, unit);
  }

  @Override
  public void addListener(Listener<T> listener) {
    result.addListener(listener);
  }

  private void doNext(List<Callable<ListenableFuture<T>>> callables, ExceptionChecker exceptionChecker) {
    Callable<ListenableFuture<T>> callable = callables.remove(0);
    try {
      ListenableFuture<T> future = callable.call();

      future.addListener(new ListenableFuture.Listener<T>() {
        @Override
        public void onSuccess(T value) {
          result.set(value);
        }

        @Override
        public void onFailure(ExecutionException e) {
          if (callables.isEmpty() || !exceptionChecker.shouldContinue(e)) {
            Log.w(TAG, e);
            result.setException(e.getCause());
          } else if (!result.isCancelled()) {
            doNext(callables, exceptionChecker);
          }
        }
      });
    } catch (Exception e) {
      if (callables.isEmpty() || !exceptionChecker.shouldContinue(e)) {
        result.setException(e.getCause());
      } else if (!result.isCancelled()) {
        Log.w(TAG, e);
        doNext(callables, exceptionChecker);
      }
    }
  }

  public interface ExceptionChecker {
    boolean shouldContinue(Exception e);
  }
}
