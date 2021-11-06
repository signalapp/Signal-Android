package org.signal.core.util.concurrent

import java.util.concurrent.Executor

/**
 * An executor that will keep track of the stack trace at the time of calling [execute] and use that to build a more useful stack trace in the event of a crash.
 */
internal class TracingExecutor(val wrapped: Executor) : Executor by wrapped {

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
}
