package org.signal.core.util.concurrent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executor

class KeyedSerialMonoLifoExecutorTest {

  @Test
  fun `first task runs immediately`() {
    val executor = TestExecutor()
    val subject = KeyedSerialMonoLifoExecutor(executor)
    val task = TestRunnable()

    subject.execute("a", task)

    assertEquals(1, executor.pending())
    executor.runNext()
    assertTrue(task.didRun)
  }

  @Test
  fun `second task is held until first completes`() {
    val executor = TestExecutor()
    val subject = KeyedSerialMonoLifoExecutor(executor)
    val first = TestRunnable()
    val second = TestRunnable()

    subject.execute("a", first)
    subject.execute("a", second)

    assertEquals(1, executor.pending())
    executor.runNext()
    assertTrue(first.didRun)
    assertFalse(second.didRun)

    assertEquals(1, executor.pending())
    executor.runNext()
    assertTrue(second.didRun)
  }

  @Test
  fun `only the latest pending task is kept`() {
    val executor = TestExecutor()
    val subject = KeyedSerialMonoLifoExecutor(executor)
    val first = TestRunnable()
    val replaced1 = TestRunnable()
    val replaced2 = TestRunnable()
    val latest = TestRunnable()

    subject.execute("a", first)
    subject.execute("a", replaced1)
    subject.execute("a", replaced2)
    subject.execute("a", latest)

    executor.runNext()
    assertTrue(first.didRun)

    executor.runNext()
    assertTrue(latest.didRun)
    assertFalse(replaced1.didRun)
    assertFalse(replaced2.didRun)

    assertEquals(0, executor.pending())
  }

  @Test
  fun `enqueue returns true when replacing a pending task`() {
    val executor = TestExecutor()
    val subject = KeyedSerialMonoLifoExecutor(executor)

    val firstReplace = subject.enqueue("a", TestRunnable())
    assertFalse(firstReplace)

    val secondReplace = subject.enqueue("a", TestRunnable())
    assertFalse(secondReplace)

    val thirdReplace = subject.enqueue("a", TestRunnable())
    assertTrue(thirdReplace)
  }

  @Test
  fun `different keys dedupe independently`() {
    val executor = TestExecutor()
    val subject = KeyedSerialMonoLifoExecutor(executor)
    val a1 = TestRunnable()
    val a2replaced = TestRunnable()
    val a3 = TestRunnable()
    val b1 = TestRunnable()
    val b2 = TestRunnable()

    subject.execute("a", a1)
    subject.execute("a", a2replaced)
    subject.execute("a", a3)
    subject.execute("b", b1)
    subject.execute("b", b2)

    // a1 and b1 should both be dispatched
    assertEquals(2, executor.pending())

    executor.runNext() // a1
    assertTrue(a1.didRun)

    executor.runNext() // b1
    assertTrue(b1.didRun)

    executor.runNext() // a3 (a2replaced was dropped)
    assertTrue(a3.didRun)
    assertFalse(a2replaced.didRun)

    executor.runNext() // b2
    assertTrue(b2.didRun)

    assertEquals(0, executor.pending())
  }

  @Test
  fun `idle keys are cleaned up`() {
    val executor = TestExecutor()
    val subject = KeyedSerialMonoLifoExecutor(executor)

    // Iteration 1: fill the queue (active + pending), drain it fully
    val a1 = TestRunnable()
    val a2 = TestRunnable()
    subject.execute("a", a1)
    subject.execute("a", a2)
    executor.runNext()
    executor.runNext()
    assertTrue(a1.didRun)
    assertTrue(a2.didRun)
    assertEquals(0, executor.pending())

    // Iteration 2: reuse the same key — should work with no stale state
    val b1 = TestRunnable()
    val b2 = TestRunnable()
    subject.execute("a", b1)
    subject.execute("a", b2)
    executor.runNext()
    executor.runNext()
    assertTrue(b1.didRun)
    assertTrue(b2.didRun)
    assertEquals(0, executor.pending())

    // Iteration 3: once more to confirm repeated cleanup
    val c1 = TestRunnable()
    val c2 = TestRunnable()
    subject.execute("a", c1)
    subject.execute("a", c2)
    executor.runNext()
    executor.runNext()
    assertTrue(c1.didRun)
    assertTrue(c2.didRun)
    assertEquals(0, executor.pending())
  }

  private class TestExecutor : Executor {
    private val tasks = ArrayDeque<Runnable>()

    override fun execute(command: Runnable) {
      tasks.addLast(command)
    }

    fun pending(): Int = tasks.size

    fun runNext() {
      tasks.removeFirst().run()
    }
  }

  private class TestRunnable : Runnable {
    var didRun = false
      private set

    override fun run() {
      didRun = true
    }
  }
}
