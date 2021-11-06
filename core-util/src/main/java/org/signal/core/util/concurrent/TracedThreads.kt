package org.signal.core.util.concurrent

import java.util.concurrent.ConcurrentHashMap

/**
 * A container for keeping track of the caller stack traces of the threads we care about.
 *
 * Note: This should only be used for debugging. To keep overhead minimal, not much effort has been put into ensuring this map is 100% accurate.
 */
internal object TracedThreads {
  val callerStackTraces: MutableMap<Long, Throwable> = ConcurrentHashMap()
}
