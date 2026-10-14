package org.thoughtcrime.securesms.groups

import androidx.annotation.WorkerThread
import org.signal.core.util.ThreadUtil
import org.signal.core.util.groups.GroupChangeBusyException
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.crypto.ReentrantSessionLock
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.messages.IncomingMessageObserver
import org.thoughtcrime.securesms.util.RemoteConfig
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A locking mechanism that allows both single group modification exclusivity and all groups modification exclusivity.
 *
 * Two tiers of locks are used, the first tier is the group specific lock (a [ReentrantLock]) and the second is an
 * all groups lock (a [ReentrantReadWriteLock]).
 *
 * To modify a single group, a thread first acquires the single group lock. Multiple threads trying to modify the same group
 * will queue up behind the single group lock and not the all groups read lock. The thread can then acquire a reader
 * lock on the [allGroupsLock]. The reader lock allows multiple single unique group modifications to run concurrently but
 * blocks if another thread takes the writer lock for all group modification exclusivity.
 *
 * To modify any group, a thread only has to acquire the writer lock of [allGroupsLock]. It blocks all single group work by not allowing
 * new read locks to be taken. It also means we don't have to acquire each group's individual lock. The lock is fair, so new single
 * group work queues behind a waiting writer instead of potentially starving it.
 *
 */
object GroupsV2ProcessingLock {

  private val TAG = Log.tag(GroupsV2ProcessingLock::class.java)

  private val DEFAULT_TIMEOUT = 5.seconds

  private val allGroupsLock = AllGroupsLock()
  private val singleGroupLocks = ConcurrentHashMap<GroupId.V2, SingleGroupLock>()

  /**
   * Acquire the all group state lock that allows exclusive modification of any groups state. This lock
   * acquires the writer lock which blocks all future single group lock threads until it is released.
   *
   * A thread cannot hold a single group lock and acquire the all groups lock at the same time. The thread
   * would deadlock trying to promote the [allGroupsLock] reader lock to a writer lock.
   *
   * This is intended to only be used by the [IncomingMessageObserver].
   */
  @WorkerThread
  @Throws(GroupChangeBusyException::class)
  fun acquireAllGroupsProcessingLock(timeout: Duration): Closeable {
    if (allGroupsLock.readHoldCount > 0 && !allGroupsLock.isWriteLockedByCurrentThread) {
      throw AssertionError("Tried to acquire the all groups lock while this thread holds a single group lock. Read holds can't be upgraded to the write lock.")
    }

    return acquireAllGroupsLock(allGroupsLock.writeLock(), timeout) { "Timed out after $timeout waiting for the all groups lock. ${describeAllGroupsLock()}" }
  }

  /**
   * Acquires the group specific lock and then an all groups reader lock to grant exclusive single group state modification. If the thread
   * already holds the group specific lock, or the all groups writer lock, this is effectively a no-op.
   *
   * A thread holding a single group lock must not acquire a different group's lock or the writer lock as that could risk
   * deadlock.
   *
   * This is currently manged for you by [GroupManager] when making any group changes.
   */
  @JvmStatic
  @JvmOverloads
  @WorkerThread
  @Throws(GroupChangeBusyException::class)
  fun acquireSingleGroupProcessingLock(groupId: GroupId.V2, timeout: Duration = DEFAULT_TIMEOUT): Closeable {
    ThreadUtil.assertNotMainThread()

    // If we already have the all groups writer, proceed
    if (allGroupsLock.isWriteLockedByCurrentThread) {
      return Closeable {}
    }

    val singleGroupLock = singleGroupLocks.computeIfAbsent(groupId) { SingleGroupLock() }

    // If we are getting this lock for the first time, check it's safe to acquire
    if (!singleGroupLock.isHeldByCurrentThread) {
      if (RemoteConfig.internalUser) {
        assertNotInTransactionOrSessionLock()
      }

      if (allGroupsLock.readHoldCount > 0) {
        val message = "Tried to acquire the single group lock for $groupId while holding another single group or group creation lock. This can deadlock."
        if (RemoteConfig.internalUser) {
          throw AssertionError(message)
        } else {
          Log.w(TAG, message, Throwable())
        }
      }
    }

    // It's safe, or we already have it, acquire the group specific lock and the all group read lock

    singleGroupLock.tryLockOrThrow(timeout) { "Timed out after $timeout waiting for the single group lock for $groupId. Holder: ${singleGroupLock.ownerName ?: "none"}." }

    try {
      allGroupsLock.readLock().tryLockOrThrow(timeout) { "Timed out after $timeout waiting for a shared hold on the all groups lock while holding the single group lock for $groupId. ${describeAllGroupsLock()}" }
    } catch (e: GroupChangeBusyException) {
      singleGroupLock.unlock()
      throw e
    }

    return Closeable {
      allGroupsLock.readLock().unlock()
      singleGroupLock.unlock()
    }
  }

  /**
   * Use during group creation only. Acquires a reader from [allGroupsLock] to allow it to make a group without interfering with the all groups exclusivity. Since the group
   * has no id until after it's created, there's no group specific lock to take.
   */
  @JvmStatic
  @WorkerThread
  @Throws(GroupChangeBusyException::class)
  fun acquireGroupCreationProcessingLock(): Closeable {
    return acquireAllGroupsLock(allGroupsLock.readLock(), DEFAULT_TIMEOUT) { "Timed out after $DEFAULT_TIMEOUT waiting for a shared hold on the all groups lock to create a group. ${describeAllGroupsLock()}" }
  }

  private fun acquireAllGroupsLock(lock: Lock, timeout: Duration, message: () -> String): Closeable {
    ThreadUtil.assertNotMainThread()

    if (allGroupsLock.isWriteLockedByCurrentThread) {
      return Closeable {}
    }

    if (RemoteConfig.internalUser && allGroupsLock.readHoldCount == 0) {
      assertNotInTransactionOrSessionLock()
    }

    lock.tryLockOrThrow(timeout, message)

    return Closeable { lock.unlock() }
  }

  private fun Lock.tryLockOrThrow(timeout: Duration, message: () -> String) {
    val acquired = try {
      tryLock(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
    } catch (e: InterruptedException) {
      Log.w(TAG, e)
      throw GroupChangeBusyException(e)
    }

    if (!acquired) {
      throw GroupChangeBusyException(message())
    }
  }

  private fun describeAllGroupsLock(): String {
    val groupHolders = singleGroupLocks.values.mapNotNull { it.ownerName }
    return "All groups lock holder: ${allGroupsLock.ownerName ?: "none"}. Waiting for all groups lock: ${allGroupsLock.queuedWriterNames.ifEmpty { "none" }}. Single group lock holders: ${groupHolders.ifEmpty { "none" }}. Shared holds: ${allGroupsLock.readLockCount}."
  }

  private fun assertNotInTransactionOrSessionLock() {
    if (SignalDatabase.inTransaction) {
      throw AssertionError("Tried to acquire a groups processing lock inside of a database transaction!")
    }
    if (ReentrantSessionLock.INSTANCE.isHeldByCurrentThread()) {
      throw AssertionError("Tried to acquire a groups processing lock inside of the ReentrantSessionLock!!")
    }
  }

  private class AllGroupsLock : ReentrantReadWriteLock(true) {
    val ownerName: String?
      get() = owner?.name

    val queuedWriterNames: List<String>
      get() = queuedWriterThreads.map { it.name }
  }

  private class SingleGroupLock : ReentrantLock() {
    val ownerName: String?
      get() = owner?.name
  }
}
