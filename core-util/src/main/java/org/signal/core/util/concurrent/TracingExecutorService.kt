package org.signal.core.util.concurrent

import java.util.Queue
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor

/**
 * An executor that will keep track of the stack trace at the time of calling [execute] and use that to build a more useful stack trace in the event of a crash.
 */
internal class TracingExecutorService(val wrapped: ExecutorService) : ExecutorService by wrapped {

  override fun execute(command: Runnable?) {
    val callerStackTrace = Throwable()

    wrapped.execute {
      val currentThread: Thread = Thread.currentThread()
      val currentHandler: Thread.UncaughtExceptionHandler? = currentThread.uncaughtExceptionHandler
      val originalHandler: Thread.UncaughtExceptionHandler? = if (currentHandler is TracingUncaughtExceptionHandler) currentHandler.originalHandler else currentHandler

      currentThread.uncaughtExceptionHandler = TracingUncaughtExceptionHandler(originalHandler, callerStackTrace)

      TracedThreads.callerStackTraces.put(currentThread.id, callerStackTrace)
      try {
        command?.run()
      } finally {
        TracedThreads.callerStackTraces.remove(currentThread.id)
      }
    }
  }

  val queue: Queue<Runnable>
    get() {
      return if (wrapped is ThreadPoolExecutor)
        wrapped.queue
      else
        LinkedBlockingQueue()
    }

  val activeCount: Int
    get() {
      return if (wrapped is ThreadPoolExecutor)
        wrapped.activeCount
      else
        0
    }

  val maximumPoolSize: Int
    get() {
      return if (wrapped is ThreadPoolExecutor)
        wrapped.maximumPoolSize
      else
        0
    }
}
