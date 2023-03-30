package org.thoughtcrime.securesms.testing

import org.signal.core.util.ThreadUtil
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import java.util.concurrent.CountDownLatch

typealias LogPredicate = (Entry) -> Boolean

/**
 * Logging implementation that holds logs in memory as they are added to be retrieve at a later time by a test.
 * Can also be used for multithreaded synchronization and waiting until certain logs are emitted before continuing
 * a test.
 */
class InMemoryLogger : Log.Logger() {

  private val executor = SignalExecutors.newCachedSingleThreadExecutor("inmemory-logger", ThreadUtil.PRIORITY_BACKGROUND_THREAD)
  private val predicates = mutableListOf<LogPredicate>()
  private val logEntries = mutableListOf<Entry>()

  override fun v(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) = add(Verbose(tag, message, t, System.currentTimeMillis()))
  override fun d(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) = add(Debug(tag, message, t, System.currentTimeMillis()))
  override fun i(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) = add(Info(tag, message, t, System.currentTimeMillis()))
  override fun w(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) = add(Warn(tag, message, t, System.currentTimeMillis()))
  override fun e(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) = add(Error(tag, message, t, System.currentTimeMillis()))

  override fun flush() {
    val latch = CountDownLatch(1)
    executor.execute { latch.countDown() }
    latch.await()
  }

  fun clear() {
    val latch = CountDownLatch(1)
    executor.execute {
      predicates.clear()
      logEntries.clear()
      latch.countDown()
    }
    latch.await()
  }

  private fun add(entry: Entry) {
    executor.execute {
      logEntries += entry

      val iterator = predicates.iterator()
      while (iterator.hasNext()) {
        val predicate = iterator.next()
        if (predicate(entry)) {
          iterator.remove()
        }
      }
    }
  }

  /** Blocks until a snapshot of all log entries can be taken in a thread-safe way. */
  fun entries(): List<Entry> {
    val latch = CountDownLatch(1)
    var entries: List<Entry> = emptyList()
    executor.execute {
      entries = logEntries.toList()
      latch.countDown()
    }
    latch.await()
    return entries
  }

  /** Returns a countdown latch that'll fire at a future point when an [Entry] is received that matches the predicate. */
  fun getLockForUntil(predicate: LogPredicate): CountDownLatch {
    val latch = CountDownLatch(1)
    executor.execute {
      predicates += { entry ->
        if (predicate(entry)) {
          latch.countDown()
          true
        } else {
          false
        }
      }
    }
    return latch
  }
}

sealed interface Entry {
  val tag: String
  val message: String?
  val throwable: Throwable?
  val timestamp: Long
}

data class Verbose(override val tag: String, override val message: String?, override val throwable: Throwable?, override val timestamp: Long) : Entry
data class Debug(override val tag: String, override val message: String?, override val throwable: Throwable?, override val timestamp: Long) : Entry
data class Info(override val tag: String, override val message: String?, override val throwable: Throwable?, override val timestamp: Long) : Entry
data class Warn(override val tag: String, override val message: String?, override val throwable: Throwable?, override val timestamp: Long) : Entry
data class Error(override val tag: String, override val message: String?, override val throwable: Throwable?, override val timestamp: Long) : Entry
