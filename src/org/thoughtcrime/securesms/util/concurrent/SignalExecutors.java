package org.thoughtcrime.securesms.util.concurrent;

import android.support.annotation.NonNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SignalExecutors {

  public static final ExecutorService IO = Executors.newCachedThreadPool(new ThreadFactory() {
    private final AtomicInteger counter = new AtomicInteger();
    @Override
    public Thread newThread(@NonNull Runnable r) {
      return new Thread(r, "signal-io-" + counter.getAndIncrement());
    }
  });


  public static ExecutorService newCachedSingleThreadExecutor(final String name) {
    ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 15, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> new Thread(r, name));
    executor.allowCoreThreadTimeOut(true);
    return executor;
  }
}
