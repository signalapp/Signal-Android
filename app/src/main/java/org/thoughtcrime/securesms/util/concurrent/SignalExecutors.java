package org.thoughtcrime.securesms.util.concurrent;

import androidx.annotation.NonNull;

import com.google.android.gms.common.util.concurrent.NumberedThreadFactory;

import org.thoughtcrime.securesms.util.LinkedBlockingLifoQueue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SignalExecutors {

  public static final ExecutorService UNBOUNDED = Executors.newCachedThreadPool(new NumberedThreadFactory("signal-unbounded"));
  public static final ExecutorService BOUNDED   = Executors.newFixedThreadPool(getIdealThreadCount(), new NumberedThreadFactory("signal-bounded"));
  public static final ExecutorService SERIAL    = Executors.newSingleThreadExecutor(new NumberedThreadFactory("signal-serial"));

  public static ExecutorService newCachedSingleThreadExecutor(final String name) {
    ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 15, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> new Thread(r, name));
    executor.allowCoreThreadTimeOut(true);
    return executor;
  }

  /**
   * Returns an executor that prioritizes newer work. This is the opposite of a traditional executor,
   * which processor work in FIFO order.
   */
  public static ExecutorService newFixedLifoThreadExecutor(String name, int minThreads, int maxThreads) {
    return new ThreadPoolExecutor(minThreads, maxThreads, 0, TimeUnit.MILLISECONDS, new LinkedBlockingLifoQueue<>(), new NumberedThreadFactory(name));
  }

  /**
   * Returns an 'ideal' thread count based on the number of available processors.
   */
  public static int getIdealThreadCount() {
    return Math.max(2, Math.min(Runtime.getRuntime().availableProcessors() - 1, 4));
  }

  private static class NumberedThreadFactory implements ThreadFactory {

    private final String        baseName;
    private final AtomicInteger counter;

    NumberedThreadFactory(@NonNull String baseName) {
      this.baseName = baseName;
      this.counter  = new AtomicInteger();
    }

    @Override
    public Thread newThread(@NonNull Runnable r) {
      return new Thread(r, baseName + "-" + counter.getAndIncrement());
    }
  }
}
