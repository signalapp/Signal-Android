package org.signal.core.util.concurrent

import org.signal.core.util.ExceptionUtil

/**
 * An uncaught exception handler that will combine a caller stack trace with the exception to print a more useful stack trace.
 */
internal class TracingUncaughtExceptionHandler (
    val originalHandler: Thread.UncaughtExceptionHandler?,
    private val callerStackTrace: Throwable) : Thread.UncaughtExceptionHandler {

  override fun uncaughtException(thread: Thread, exception: Throwable) {
    val updated = ExceptionUtil.joinStackTrace(exception, callerStackTrace)
    originalHandler?.uncaughtException(thread, updated)
  }
}