package org.thoughtcrime.securesms.util.concurrent;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;

/**
 * Allows you to specify a filter upon which a job will be executed on the provided executor. If
 * it doesn't match the filter, it will be run on the calling thread.
 */
public final class FilteredExecutor implements Executor {

  private final Executor backgroundExecutor;
  private final Filter   filter;

  public FilteredExecutor(@NonNull Executor backgroundExecutor, @NonNull Filter filter) {
    this.backgroundExecutor = backgroundExecutor;
    this.filter             = filter;
  }

  @Override
  public void execute(@NonNull Runnable runnable) {
    if (filter.shouldRunOnExecutor()) {
      backgroundExecutor.execute(runnable);
    } else {
      runnable.run();
    }
  }

  public interface Filter {
    boolean shouldRunOnExecutor();
  }
}
