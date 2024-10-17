package org.thoughtcrime.securesms.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.concurrent.SignalExecutors
import org.thoughtcrime.securesms.testing.SignalFlakyTestRule
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * These are tests for the wrapper we wrote around SQLCipherDatabase, not the stock or SQLCipher one.
 */
@RunWith(AndroidJUnit4::class)
class SQLiteDatabaseTest {

  private lateinit var db: SQLiteDatabase

  @get:Rule
  val flakyTestRule = SignalFlakyTestRule()

  @Before
  fun setup() {
    db = SignalDatabase.instance!!.signalWritableDatabase
  }

  @Test
  fun runPostSuccessfulTransaction_runsImmediatelyIfNotInTransaction() {
    val hasRun = AtomicBoolean(false)
    db.runPostSuccessfulTransaction { hasRun.set(true) }
    assertTrue(hasRun.get())
  }

  @Test
  fun runPostSuccessfulTransaction_runsAfterSuccessIfInTransaction() {
    val hasRun = AtomicBoolean(false)

    db.beginTransaction()

    db.runPostSuccessfulTransaction { hasRun.set(true) }
    assertFalse(hasRun.get())

    db.setTransactionSuccessful()
    db.endTransaction()

    assertTrue(hasRun.get())
  }

  @Test
  fun runPostSuccessfulTransaction_doesNotRunAfterFailedTransaction() {
    val hasRun = AtomicBoolean(false)

    db.beginTransaction()

    db.runPostSuccessfulTransaction { hasRun.set(true) }
    assertFalse(hasRun.get())

    db.endTransaction()

    assertFalse(hasRun.get())

    // Verifying we still don't run it even after a subsequent success
    db.beginTransaction()
    db.setTransactionSuccessful()
    db.endTransaction()

    assertFalse(hasRun.get())
  }

  @Test
  fun runPostSuccessfulTransaction_onlyRunAfterAllTransactionsComplete() {
    val hasRun = AtomicBoolean(false)

    db.beginTransaction()

    db.runPostSuccessfulTransaction { hasRun.set(true) }
    assertFalse(hasRun.get())

    db.beginTransaction()
    db.setTransactionSuccessful()
    db.endTransaction()
    assertFalse(hasRun.get())

    db.setTransactionSuccessful()
    db.endTransaction()

    assertTrue(hasRun.get())
  }

  @Test
  fun runPostSuccessfulTransaction_runsImmediatelyIfTheTransactionIsOnAnotherThread() {
    db.beginTransaction()

    val latch = CountDownLatch(1)
    SignalExecutors.BOUNDED.execute {
      val hasRun = AtomicBoolean(false)
      db.runPostSuccessfulTransaction { hasRun.set(true) }
      assertTrue(hasRun.get())
      latch.countDown()
    }

    latch.await()

    db.setTransactionSuccessful()
    db.endTransaction()
  }

  @Test
  fun runPostSuccessfulTransaction_runsAfterSuccessIfInTransaction_ignoreDuplicates() {
    val hasRun1 = AtomicBoolean(false)
    val hasRun2 = AtomicBoolean(false)

    db.beginTransaction()

    db.runPostSuccessfulTransaction("key") { hasRun1.set(true) }
    db.runPostSuccessfulTransaction("key") { hasRun2.set(true) }
    assertFalse(hasRun1.get())
    assertFalse(hasRun2.get())

    db.setTransactionSuccessful()
    db.endTransaction()

    assertTrue(hasRun1.get())
    assertFalse(hasRun2.get())
  }

  @Test
  fun runPostSuccessfulTransaction_runsAndPerformsAnotherTransaction() {
    val hasRun = AtomicBoolean(false)

    db.beginTransaction()

    db.runPostSuccessfulTransaction {
      try {
        db.beginTransaction()
        hasRun.set(true)
        db.setTransactionSuccessful()
      } finally {
        db.endTransaction()
      }
    }

    assertFalse(hasRun.get())

    db.setTransactionSuccessful()
    db.endTransaction()

    assertTrue(hasRun.get())
  }

  @Test
  fun runPostSuccessfulTransaction_runsAndPerformsAnotherTransactionAndRunPostNested() {
    val hasRun1 = AtomicBoolean(false)
    val hasRun2 = AtomicBoolean(false)

    db.beginTransaction()

    db.runPostSuccessfulTransaction {
      db.beginTransaction()

      db.runPostSuccessfulTransaction {
        assertTrue(hasRun1.get())
        assertFalse(hasRun2.get())
        hasRun2.set(true)
      }

      assertFalse(hasRun1.get())
      hasRun1.set(true)
      assertFalse(hasRun2.get())

      db.setTransactionSuccessful()
      db.endTransaction()
    }

    assertFalse(hasRun1.get())
    assertFalse(hasRun2.get())

    db.setTransactionSuccessful()
    db.endTransaction()

    assertTrue(hasRun1.get())
    assertTrue(hasRun2.get())
  }

//  @SignalFlakyTest
//  @Test
  fun runPostSuccessfulTransaction_runsAfterMainTransactionInNestedTransaction() {
    val hasRun1 = AtomicBoolean(false)
    val hasRun2 = AtomicBoolean(false)

    db.beginTransaction()

    db.runPostSuccessfulTransaction {
      assertFalse(hasRun1.get())
      assertFalse(hasRun2.get())
      hasRun1.set(true)
    }

    assertFalse(hasRun1.get())
    assertFalse(hasRun2.get())

    db.beginTransaction()

    db.runPostSuccessfulTransaction {
      assertTrue(hasRun1.get())
      assertFalse(hasRun2.get())
      hasRun2.set(true)
    }

    db.setTransactionSuccessful()

    assertFalse(hasRun1.get())
    assertFalse(hasRun2.get())

    db.endTransaction()
    db.setTransactionSuccessful()

    assertFalse(hasRun1.get())
    assertFalse(hasRun2.get())

    db.endTransaction()

    assertTrue(hasRun1.get())
    assertTrue(hasRun2.get())
  }
}
