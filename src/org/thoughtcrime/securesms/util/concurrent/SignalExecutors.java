package org.thoughtcrime.securesms.util.concurrent;

import android.support.annotation.NonNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class SignalExecutors {

  public static final ExecutorService IO = Executors.newCachedThreadPool(new ThreadFactory() {
    private final AtomicInteger counter = new AtomicInteger();
    @Override
    public Thread newThread(@NonNull Runnable r) {
      return new Thread(r, "signal-io-" + counter.getAndIncrement());
    }
  });
}
