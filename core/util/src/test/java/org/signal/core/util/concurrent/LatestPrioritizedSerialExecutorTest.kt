package org.signal.core.util.concurrent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executor

class LatestPrioritizedSerialExecutorTest {
  @Test
  fun execute_sortsInPriorityOrder() {
    val executor = TestExecutor()
    val placeholder = TestRunnable()

    val first = TestRunnable()
    val second = TestRunnable()
    val third = TestRunnable()

    val subject = LatestPrioritizedSerialExecutor(executor)
    subject.execute(0, placeholder) // The first thing we execute can't be sorted, so we put in this placeholder
    subject.execute(1, third)
    subject.execute(2, second)
    subject.execute(3, first)

    executor.next() // Clear the placeholder task

    executor.next()
    assertTrue(first.didRun)

    executor.next()
    assertTrue(second.didRun)

    executor.next()
    assertTrue(third.didRun)
  }

  @Test
  fun execute_replacesDupes() {
    val executor = TestExecutor()
    val placeholder = TestRunnable()

    val firstReplaced = TestRunnable()
    val first = TestRunnable()
    val second = TestRunnable()
    val thirdReplaced = TestRunnable()
    val third = TestRunnable()

    val subject = LatestPrioritizedSerialExecutor(executor)
    subject.execute(0, placeholder) // The first thing we execute can't be sorted, so we put in this placeholder
    subject.execute(1, thirdReplaced)
    subject.execute(1, third)
    subject.execute(2, second)
    subject.execute(3, firstReplaced)
    subject.execute(3, first)

    executor.next() // Clear the placeholder task

    executor.next()
    assertTrue(first.didRun)

    executor.next()
    assertTrue(second.didRun)

    executor.next()
    assertTrue(third.didRun)

    assertFalse(firstReplaced.didRun)
    assertFalse(thirdReplaced.didRun)
  }

  private class TestExecutor : Executor {
    private val tasks = ArrayDeque<Runnable>()

    override fun execute(command: Runnable) {
      tasks.add(command)
    }

    fun next() {
      tasks.removeLast().run()
    }
  }

  class TestRunnable : Runnable {
    private var _didRun = false
    val didRun get() = _didRun

    override fun run() {
      _didRun = true
    }
  }
}
