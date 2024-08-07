package org.signal.core.util.concurrent;

import android.os.HandlerThread;
import android.os.Process;

import androidx.annotation.NonNull;

import org.signal.core.util.LinkedBlockingLifoQueue;
import org.signal.core.util.ThreadUtil;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class SignalExecutors {

  public static final ExecutorService UNBOUNDED  = Executors.newCachedThreadPool(new NumberedThreadFactory("signal-unbounded", ThreadUtil.PRIORITY_BACKGROUND_THREAD));
  public static final ExecutorService BOUNDED    = Executors.newFixedThreadPool(4, new NumberedThreadFactory("signal-bounded", ThreadUtil.PRIORITY_BACKGROUND_THREAD));
  public static final ExecutorService SERIAL     = Executors.newSingleThreadExecutor(new NumberedThreadFactory("signal-serial", ThreadUtil.PRIORITY_BACKGROUND_THREAD));
  public static final ExecutorService BOUNDED_IO = newCachedBoundedExecutor("signal-io-bounded", ThreadUtil.PRIORITY_IMPORTANT_BACKGROUND_THREAD, 1, 32, 30);

  private SignalExecutors() {}

  public static ExecutorService newCachedSingleThreadExecutor(final String name, int priority) {
    ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 15, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> new Thread(r, name) {
      @Override public void run() {
        Process.setThreadPriority(priority);
        super.run();
      }
    });
    executor.allowCoreThreadTimeOut(true);
    return executor;
  }

  /**
   * ThreadPoolExecutor will only create a new thread if the provided queue returns false from
   * offer(). That means if you give it an unbounded queue, it'll only ever create 1 thread, no
   * matter how long the queue gets.
   * <p>
   * But if you bound the queue and submit more runnables than there are threads, your task is
   * rejected and throws an exception.
   * <p>
   * So we make a queue that will always return false if it's non-empty to ensure new threads get
   * created. Then, if a task gets rejected, we simply add it to the queue.
   */
  public static ExecutorService newCachedBoundedExecutor(final String name, int priority, int minThreads, int maxThreads, int timeoutSeconds) {
    ThreadPoolExecutor threadPool = new ThreadPoolExecutor(minThreads,
                                                           maxThreads,
                                                           timeoutSeconds,
                                                           TimeUnit.SECONDS,
                                                           new LinkedBlockingQueue<Runnable>() {
                                                             @Override
                                                             public boolean offer(Runnable runnable) {
                                                               if (isEmpty()) {
                                                                 return super.offer(runnable);
                                                               } else {
                                                                 return false;
                                                               }
                                                             }
                                                           }, new NumberedThreadFactory(name, priority));

    threadPool.setRejectedExecutionHandler((runnable, executor) -> {
      try {
        executor.getQueue().put(runnable);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    });

    return threadPool;
  }

  /**
   * Returns an executor that prioritizes newer work. This is the opposite of a traditional executor,
   * which processor work in FIFO order.
   */
  public static ExecutorService newFixedLifoThreadExecutor(String name, int minThreads, int maxThreads) {
    return new ThreadPoolExecutor(minThreads, maxThreads, 0, TimeUnit.MILLISECONDS, new LinkedBlockingLifoQueue<>(), new NumberedThreadFactory(name, ThreadUtil.PRIORITY_BACKGROUND_THREAD));
  }

  public static HandlerThread getAndStartHandlerThread(@NonNull String name, int priority) {
    HandlerThread handlerThread = new HandlerThread(name, priority);
    handlerThread.start();
    return handlerThread;
  }

  public static class NumberedThreadFactory implements ThreadFactory {

    private final int           priority;
    private final String        baseName;
    private final AtomicInteger counter;

    public NumberedThreadFactory(@NonNull String baseName, int priority) {
      this.priority = priority;
      this.baseName = baseName;
      this.counter  = new AtomicInteger();
    }

    @Override
    public Thread newThread(@NonNull Runnable r) {
      return new Thread(r, baseName + "-" + counter.getAndIncrement()) {
        @Override
        public void run() {
          Process.setThreadPriority(priority);
          super.run();
        }
      };
    }
  }
}
