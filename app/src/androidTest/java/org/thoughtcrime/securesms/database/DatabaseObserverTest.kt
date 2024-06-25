package org.thoughtcrime.securesms.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.concurrent.SignalExecutors
import org.thoughtcrime.securesms.dependencies.AppDependencies
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * When writing tests, be very careful to call [DatabaseObserver.flush] before asserting any observer state. Internally, the observer is enqueueing tasks on
 * an executor, and failing to flush the executor will lead to incorrect/flaky tests.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseObserverTest {

  private lateinit var db: SQLiteDatabase
  private lateinit var observer: DatabaseObserver

  @Before
  fun setup() {
    db = SignalDatabase.instance!!.signalWritableDatabase
    observer = AppDependencies.databaseObserver
  }

  @Test
  fun notifyConversationListeners_runsImmediatelyIfNotInTransaction() {
    val hasRun = AtomicBoolean(false)
    observer.registerConversationObserver(1) { hasRun.set(true) }
    observer.notifyConversationListeners(1)
    observer.flush()
    assertTrue(hasRun.get())
  }

  @Test
  fun notifyConversationListeners_runsAfterSuccessIfInTransaction() {
    val hasRun = AtomicBoolean(false)

    db.beginTransaction()

    observer.registerConversationObserver(1) { hasRun.set(true) }
    observer.notifyConversationListeners(1)
    observer.flush()
    assertFalse(hasRun.get())

    db.setTransactionSuccessful()
    db.endTransaction()

    observer.flush()

    assertTrue(hasRun.get())
  }

  @Test
  fun notifyConversationListeners_doesNotRunAfterFailedTransaction() {
    val hasRun = AtomicBoolean(false)

    db.beginTransaction()

    observer.registerConversationObserver(1) { hasRun.set(true) }
    observer.notifyConversationListeners(1)
    observer.flush()
    assertFalse(hasRun.get())

    db.endTransaction()
    observer.flush()
    assertFalse(hasRun.get())

    // Verifying we still don't run it even after a subsequent success
    db.beginTransaction()
    db.setTransactionSuccessful()
    db.endTransaction()

    observer.flush()

    assertFalse(hasRun.get())
  }

  @Test
  fun notifyConversationListeners_onlyRunAfterAllTransactionsComplete() {
    val hasRun = AtomicBoolean(false)

    db.beginTransaction()

    observer.registerConversationObserver(1) { hasRun.set(true) }
    observer.notifyConversationListeners(1)
    observer.flush()
    assertFalse(hasRun.get())

    db.beginTransaction()
    db.setTransactionSuccessful()
    db.endTransaction()
    observer.flush()
    assertFalse(hasRun.get())

    db.setTransactionSuccessful()
    db.endTransaction()

    observer.flush()

    assertTrue(hasRun.get())
  }

  @Test
  fun notifyConversationListeners_runsImmediatelyIfTheTransactionIsOnAnotherThread() {
    db.beginTransaction()

    val latch = CountDownLatch(1)
    SignalExecutors.BOUNDED.execute {
      val hasRun = AtomicBoolean(false)

      observer.registerConversationObserver(1) { hasRun.set(true) }
      observer.notifyConversationListeners(1)
      observer.flush()
      assertTrue(hasRun.get())

      latch.countDown()
    }

    latch.await()

    db.setTransactionSuccessful()
    db.endTransaction()
  }

  @Test
  fun notifyConversationListeners_runsAfterSuccessIfInTransaction_ignoreDuplicateNotifications() {
    val thread1Count = AtomicInteger(0)
    val thread2Count = AtomicInteger(0)

    db.beginTransaction()

    observer.registerConversationObserver(1) { thread1Count.incrementAndGet() }
    observer.registerConversationObserver(2) { thread2Count.incrementAndGet() }

    observer.notifyConversationListeners(1)
    observer.notifyConversationListeners(2)
    observer.notifyConversationListeners(2)

    observer.flush()
    assertEquals(0, thread1Count.get())
    assertEquals(0, thread2Count.get())

    db.setTransactionSuccessful()
    db.endTransaction()

    observer.flush()
    assertEquals(1, thread1Count.get())
    assertEquals(1, thread2Count.get())
  }
}
