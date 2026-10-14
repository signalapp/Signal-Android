package org.thoughtcrime.securesms.groups

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.signal.core.util.ThreadUtil
import org.signal.core.util.groups.GroupChangeBusyException
import org.signal.libsignal.zkgroup.groups.GroupIdentifier
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.util.RemoteConfig
import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class GroupsV2ProcessingLockTest {

  private val group1 = groupId(1)
  private val group2 = groupId(2)

  private lateinit var executor: ExecutorService

  @Before
  fun setUp() {
    mockkStatic(ThreadUtil::class)
    every { ThreadUtil.isMainThread() } returns false
    mockkObject(RemoteConfig)
    mockkStatic(RemoteConfig::class)
    mockkObject(SignalDatabase.Companion)
    every { RemoteConfig.internalUser } returns false
    executor = Executors.newCachedThreadPool()
  }

  @After
  fun tearDown() {
    executor.shutdownNow()
    unmockkObject(SignalDatabase.Companion)
    unmockkStatic(RemoteConfig::class)
    unmockkObject(RemoteConfig)
    unmockkStatic(ThreadUtil::class)
  }

  @Test
  fun `different groups can be locked at the same time`() {
    val holder = holdSingleGroup(group1)

    val other = acquireSingleGroup(group2)

    assertThat(other.acquired.await(1, TimeUnit.SECONDS)).isTrue()

    other.release()
    holder.release()
  }

  @Test
  fun `same group waits for the current holder`() {
    val holder = holdSingleGroup(group1)

    val waiter = acquireSingleGroup(group1)
    assertThat(waiter.acquired.await(SHORT_WAIT_MS, TimeUnit.MILLISECONDS)).isFalse()

    holder.release()
    assertThat(waiter.acquired.await(1, TimeUnit.SECONDS)).isTrue()

    waiter.release()
  }

  @Test
  fun `all groups lock waits for single group holders`() {
    val holder = holdSingleGroup(group1)

    val retrieval = acquireAllGroups()
    assertThat(retrieval.acquired.await(SHORT_WAIT_MS, TimeUnit.MILLISECONDS)).isFalse()

    holder.release()
    assertThat(retrieval.acquired.await(1, TimeUnit.SECONDS)).isTrue()

    retrieval.release()
  }

  @Test
  fun `waiting all groups lock blocks new single group work`() {
    val holder = holdSingleGroup(group1)

    val retrieval = acquireAllGroups()
    Thread.sleep(SHORT_WAIT_MS)

    val newWork = acquireSingleGroup(group2)
    assertThat(newWork.acquired.await(SHORT_WAIT_MS, TimeUnit.MILLISECONDS)).isFalse()

    holder.release()
    assertThat(retrieval.acquired.await(1, TimeUnit.SECONDS)).isTrue()
    assertThat(newWork.acquired.await(SHORT_WAIT_MS, TimeUnit.MILLISECONDS)).isFalse()

    retrieval.release()
    assertThat(newWork.acquired.await(1, TimeUnit.SECONDS)).isTrue()

    newWork.release()
  }

  @Test
  fun `single group holder can reacquire its group while all groups lock is waiting`() {
    val holder = holdSingleGroup(group1)

    val retrieval = acquireAllGroups()
    Thread.sleep(SHORT_WAIT_MS)

    val reacquired = holder.run { GroupsV2ProcessingLock.acquireSingleGroupProcessingLock(group1).close() }
    reacquired.get(1, TimeUnit.SECONDS)

    holder.release()
    assertThat(retrieval.acquired.await(1, TimeUnit.SECONDS)).isTrue()

    retrieval.release()
  }

  @Test
  fun `all groups holder skips single group locks held by waiting single group work`() {
    val retrieval = holdAllGroups()

    val waiter = acquireSingleGroup(group1)
    Thread.sleep(SHORT_WAIT_MS)
    assertThat(waiter.acquired.count).isEqualTo(1L)

    val inline = retrieval.run { GroupsV2ProcessingLock.acquireSingleGroupProcessingLock(group1).close() }
    inline.get(1, TimeUnit.SECONDS)

    retrieval.release()
    assertThat(waiter.acquired.await(1, TimeUnit.SECONDS)).isTrue()

    waiter.release()
  }

  @Test
  fun `single group lock is released when the shared all groups hold times out`() {
    val retrieval = holdAllGroups()

    val timedOut = executor.submit { GroupsV2ProcessingLock.acquireSingleGroupProcessingLock(group1, 100.milliseconds).close() }
    assertFailure { timedOut.get(1, TimeUnit.SECONDS) }.transform { it.cause }.isNotNull().isInstanceOf(GroupChangeBusyException::class)

    retrieval.release()

    val next = acquireSingleGroup(group1)
    assertThat(next.acquired.await(1, TimeUnit.SECONDS)).isTrue()

    next.release()
  }

  @Test
  fun `acquiring all groups lock while holding a single group lock fails`() {
    val holder = holdSingleGroup(group1)

    val upgrade = holder.run { GroupsV2ProcessingLock.acquireAllGroupsProcessingLock(1.seconds).close() }

    assertFailure { upgrade.get(1, TimeUnit.SECONDS) }.transform { it.cause }.isNotNull().isInstanceOf(AssertionError::class)

    holder.release()
  }

  @Test
  fun `acquiring a different single group lock while holding a single group lock fails for internal users`() {
    asInternalUser()
    val holder = holdSingleGroup(group1)

    val nested = holder.run { GroupsV2ProcessingLock.acquireSingleGroupProcessingLock(group2).close() }

    assertFailure { nested.get(1, TimeUnit.SECONDS) }.transform { it.cause }.isNotNull().isInstanceOf(AssertionError::class)

    holder.release()
  }

  @Test
  fun `acquiring a single group lock while holding the group creation lock fails for internal users`() {
    asInternalUser()
    val creator = Holder { GroupsV2ProcessingLock.acquireGroupCreationProcessingLock() }
    assertThat(creator.acquired.await(1, TimeUnit.SECONDS)).isTrue()

    val nested = creator.run { GroupsV2ProcessingLock.acquireSingleGroupProcessingLock(group1).close() }

    assertFailure { nested.get(1, TimeUnit.SECONDS) }.transform { it.cause }.isNotNull().isInstanceOf(AssertionError::class)

    creator.release()
  }

  private fun asInternalUser() {
    every { RemoteConfig.internalUser } returns true
    every { SignalDatabase.inTransaction } returns false
  }

  private fun holdSingleGroup(groupId: GroupId.V2): Holder {
    return acquireSingleGroup(groupId).also { assertThat(it.acquired.await(1, TimeUnit.SECONDS)).isTrue() }
  }

  private fun holdAllGroups(): Holder {
    return acquireAllGroups().also { assertThat(it.acquired.await(1, TimeUnit.SECONDS)).isTrue() }
  }

  private fun acquireSingleGroup(groupId: GroupId.V2): Holder {
    return Holder { GroupsV2ProcessingLock.acquireSingleGroupProcessingLock(groupId) }
  }

  private fun acquireAllGroups(): Holder {
    return Holder { GroupsV2ProcessingLock.acquireAllGroupsProcessingLock(5.seconds) }
  }

  private inner class Holder(acquire: () -> Closeable) {
    val acquired = CountDownLatch(1)

    private val tasks = LinkedBlockingQueue<Runnable>()
    private val done = Runnable { }

    init {
      executor.execute {
        acquire().use {
          acquired.countDown()
          while (true) {
            val task = tasks.take()
            if (task === done) {
              break
            }
            task.run()
          }
        }
      }
    }

    fun run(block: () -> Unit): Future<Unit> {
      val future = FutureTask { block() }
      tasks.put(future)
      return future
    }

    fun release() {
      tasks.put(done)
    }
  }

  companion object {
    private const val SHORT_WAIT_MS = 200L

    private fun groupId(seed: Int): GroupId.V2 {
      return GroupId.v2(GroupIdentifier(ByteArray(32) { seed.toByte() }))
    }
  }
}
