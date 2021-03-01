package org.thoughtcrime.securesms.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.ThreadUtil;

public final class AsynchronousCallback {

  /**
   * Use to call back from a asynchronous repository call, e.g. a load operation.
   * <p>
   * Using the original thread used for operation to invoke the callback methods.
   * <p>
   * The contract is that exactly one method on the callback will be called, exactly once.
   *
   * @param <R> Result type
   * @param <E> Error type
   */
  public interface WorkerThread<R, E> {

    @androidx.annotation.WorkerThread
    void onComplete(@Nullable R result);

    @androidx.annotation.WorkerThread
    void onError(@Nullable E error);
  }

  /**
   * Use to call back from a asynchronous repository call, e.g. a load operation.
   * <p>
   * Using the main thread used for operation to invoke the callback methods.
   * <p>
   * The contract is that exactly one method on the callback will be called, exactly once.
   *
   * @param <R> Result type
   * @param <E> Error type
   */
  public interface MainThread<R, E> {

    @androidx.annotation.MainThread
    void onComplete(@Nullable R result);

    @androidx.annotation.MainThread
    void onError(@Nullable E error);


    /**
     * If you have a callback that is only suitable for running on the main thread, this will
     * decorate it to make it suitable to pass as a worker thread callback.
     */
    default @NonNull WorkerThread<R, E> toWorkerCallback() {
      return new WorkerThread<R, E>() {
        @Override
        public void onComplete(@Nullable R result) {
          ThreadUtil.runOnMain(() -> MainThread.this.onComplete(result));
        }

        @Override
        public void onError(@Nullable E error) {
          ThreadUtil.runOnMain(() -> MainThread.this.onError(error));
        }
      };
    }
  }
}
