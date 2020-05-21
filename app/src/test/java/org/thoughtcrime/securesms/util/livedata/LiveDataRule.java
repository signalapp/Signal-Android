package org.thoughtcrime.securesms.util.livedata;

import androidx.annotation.NonNull;
import androidx.arch.core.executor.ArchTaskExecutor;
import androidx.arch.core.executor.TaskExecutor;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

/**
 * Copy of androidx.arch.core.executor.testing.InstantTaskExecutorRule.
 * <p>
 * I didn't want to bring in androidx.arch.core:core-testing at this time.
 */
public final class LiveDataRule extends TestWatcher {
  @Override
  protected void starting(Description description) {
    super.starting(description);

    ArchTaskExecutor.getInstance().setDelegate(new TaskExecutor() {
      @Override
      public void executeOnDiskIO(@NonNull Runnable runnable) {
        runnable.run();
      }

      @Override
      public void postToMainThread(@NonNull Runnable runnable) {
        runnable.run();
      }

      @Override
      public boolean isMainThread() {
        return true;
      }
    });
  }

  @Override
  protected void finished(Description description) {
    super.finished(description);
    ArchTaskExecutor.getInstance().setDelegate(null);
  }
}
