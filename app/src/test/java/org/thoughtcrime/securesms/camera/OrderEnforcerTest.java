package org.thoughtcrime.securesms.camera;

import org.junit.Test;
import org.thoughtcrime.securesms.mediasend.OrderEnforcer;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class OrderEnforcerTest {

  @Test
  public void markCompleted_singleEntry() {
    AtomicInteger counter = new AtomicInteger(0);

    OrderEnforcer<Stage> enforcer = new OrderEnforcer<>(Stage.A, Stage.B, Stage.C, Stage.D);
    enforcer.run(Stage.A, new CountRunnable(counter));
    assertEquals(0, counter.get());

    enforcer.markCompleted(Stage.A);
    assertEquals(1, counter.get());
  }

  @Test
  public void markCompleted_singleEntry_waterfall() {
    AtomicInteger counter = new AtomicInteger(0);

    OrderEnforcer<Stage> enforcer = new OrderEnforcer<>(Stage.A, Stage.B, Stage.C, Stage.D);
    enforcer.run(Stage.C, new CountRunnable(counter));
    assertEquals(0, counter.get());

    enforcer.markCompleted(Stage.A);
    assertEquals(0, counter.get());

    enforcer.markCompleted(Stage.C);
    assertEquals(0, counter.get());

    enforcer.markCompleted(Stage.B);
    assertEquals(1, counter.get());
  }

  @Test
  public void markCompleted_multipleEntriesPerStage_waterfall() {
    AtomicInteger counter = new AtomicInteger(0);

    OrderEnforcer<Stage> enforcer = new OrderEnforcer<>(Stage.A, Stage.B, Stage.C, Stage.D);

    enforcer.run(Stage.A, new CountRunnable(counter));
    enforcer.run(Stage.A, new CountRunnable(counter));
    assertEquals(0, counter.get());

    enforcer.run(Stage.B, new CountRunnable(counter));
    enforcer.run(Stage.B, new CountRunnable(counter));
    assertEquals(0, counter.get());

    enforcer.run(Stage.C, new CountRunnable(counter));
    enforcer.run(Stage.C, new CountRunnable(counter));
    assertEquals(0, counter.get());

    enforcer.run(Stage.D, new CountRunnable(counter));
    enforcer.run(Stage.D, new CountRunnable(counter));
    assertEquals(0, counter.get());

    enforcer.markCompleted(Stage.A);
    assertEquals(counter.get(), 2);

    enforcer.markCompleted(Stage.D);
    assertEquals(counter.get(), 2);

    enforcer.markCompleted(Stage.B);
    assertEquals(counter.get(), 4);

    enforcer.markCompleted(Stage.C);
    assertEquals(counter.get(), 8);
  }

  @Test
  public void run_alreadyCompleted() {
    AtomicInteger counter = new AtomicInteger(0);

    OrderEnforcer<Stage> enforcer = new OrderEnforcer<>(Stage.A, Stage.B, Stage.C, Stage.D);
    enforcer.markCompleted(Stage.A);
    enforcer.markCompleted(Stage.B);

    enforcer.run(Stage.B, new CountRunnable(counter));
    assertEquals(1, counter.get());
  }

  private static class CountRunnable implements Runnable {
    private final AtomicInteger counter;

    public CountRunnable(AtomicInteger counter) {
      this.counter = counter;
    }

    @Override
    public void run() {
      counter.incrementAndGet();
    }
  }

  private enum Stage {
    A, B, C, D
  }
}
