package org.thoughtcrime.securesms.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class DeferredTest {

  private int accumulator = 0;

  private final Runnable incrementAccumulator = () -> accumulator++;
  private final Deferred testSubject          = new Deferred();

  @Test
  public void givenANullRunnable_whenISetDeferredToFalse_thenIDoNotThrow() {
    // GIVEN
    testSubject.defer(null);

    // WHEN
    testSubject.setDeferred(false);
  }

  @Test
  public void givenADeferredRunnable_whenIDeferADifferentRunnableAndSetDeferredFalse_thenIExpectOnlySecondRunnableToExecute() {
    // GIVEN
    testSubject.defer(() -> fail("This runnable should never execute"));

    // WHEN
    testSubject.defer(incrementAccumulator);
    testSubject.setDeferred(false);

    // THEN
    assertEquals(1, accumulator);
  }

  @Test
  public void givenSetDeferredFalse_whenIDeferARunnable_thenIExecuteImmediately() {
    // GIVEN
    testSubject.setDeferred(false);

    // WHEN
    testSubject.defer(incrementAccumulator);

    // THEN
    assertEquals(1, accumulator);
  }

  @Test
  public void givenSetDeferredFalse_whenISetToTrueAndDeferRunnable_thenIDoNotExecute() {
    // GIVEN
    testSubject.setDeferred(false);

    // WHEN
    testSubject.setDeferred(true);
    testSubject.defer(incrementAccumulator);

    // THEN
    assertEquals(0, accumulator);
  }

  @Test
  public void givenDeferredRunnable_whenIDeferNullAndSetDeferredFalse_thenIDoNotExecute() {
    // GIVEN
    testSubject.defer(incrementAccumulator);

    // WHEN
    testSubject.defer(null);
    testSubject.setDeferred(true);

    // THEN
    assertEquals(0, accumulator);
  }
}
