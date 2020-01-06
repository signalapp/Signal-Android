package org.thoughtcrime.securesms.testutil;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;

/**
 * Executor that runs runnables on the same thread {@link #execute(Runnable)} is invoked on.
 * Only intended to be used for tests.
 */
public class DirectExecutor implements Executor {
  @Override
  public void execute(@NonNull Runnable runnable) {
    runnable.run();
  }
}
