package org.thoughtcrime.securesms.util;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ThreadUtil {

  public static ExecutorService newDynamicSingleThreadedExecutor() {
    ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS,
                                                         new LinkedBlockingQueue<Runnable>());
    executor.allowCoreThreadTimeOut(true);

    return executor;
  }

}
