package org.thoughtcrime.securesms.util.concurrent;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.util.Util;

import java.util.concurrent.Executor;

/**
 * If submitted on the main thread, the task will be executed on the provided background executor.
 * Otherwise, the task will be run on the calling thread.
 */
public final class NonMainThreadExecutor implements Executor {

  private final Executor backgroundExecutor;

  public NonMainThreadExecutor(@NonNull Executor backgroundExecutor) {
    this.backgroundExecutor = backgroundExecutor;
  }

  @Override
  public void execute(@NonNull Runnable runnable) {
    if (Util.isMainThread()) {
      backgroundExecutor.execute(runnable);
    } else {
      runnable.run();
    }
  }
}
