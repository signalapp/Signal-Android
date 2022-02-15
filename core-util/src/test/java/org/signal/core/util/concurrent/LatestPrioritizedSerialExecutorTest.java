package org.signal.core.util.concurrent;

import org.junit.Test;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Executor;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

public final class LatestPrioritizedSerialExecutorTest {

  @Test
  public void execute_sortsInPriorityOrder() {
    TestExecutor executor    = new TestExecutor();
    Runnable     placeholder = new TestRunnable();

    Runnable first  = spy(new TestRunnable());
    Runnable second = spy(new TestRunnable());
    Runnable third  = spy(new TestRunnable());

    LatestPrioritizedSerialExecutor subject = new LatestPrioritizedSerialExecutor(executor);
    subject.execute(0, placeholder); // The first thing we execute can't be sorted, so we put in this placeholder
    subject.execute(1, third);
    subject.execute(2, second);
    subject.execute(3, first);

    executor.next(); // Clear the placeholder task

    executor.next();
    verify(first).run();

    executor.next();
    verify(second).run();

    executor.next();
    verify(third).run();
  }

  @Test
  public void execute_replacesDupes() {
    TestExecutor executor    = new TestExecutor();
    Runnable     placeholder = new TestRunnable();

    Runnable firstReplaced = spy(new TestRunnable());
    Runnable first         = spy(new TestRunnable());
    Runnable second        = spy(new TestRunnable());
    Runnable thirdReplaced = spy(new TestRunnable());
    Runnable third         = spy(new TestRunnable());

    LatestPrioritizedSerialExecutor subject = new LatestPrioritizedSerialExecutor(executor);
    subject.execute(0, placeholder); // The first thing we execute can't be sorted, so we put in this placeholder
    subject.execute(1, thirdReplaced);
    subject.execute(1, third);
    subject.execute(2, second);
    subject.execute(3, firstReplaced);
    subject.execute(3, first);

    executor.next(); // Clear the placeholder task

    executor.next();
    verify(first).run();

    executor.next();
    verify(second).run();

    executor.next();
    verify(third).run();

    verify(firstReplaced, never()).run();
    verify(thirdReplaced, never()).run();
  }

  private static final class TestExecutor implements Executor {

    private final Queue<Runnable> tasks = new LinkedList<>();

    @Override
    public void execute(Runnable command) {
      tasks.add(command);
    }

    public void next() {
      tasks.remove().run();
    }
  }

  public static class TestRunnable implements Runnable {
    @Override
    public void run() { }
  }
}
