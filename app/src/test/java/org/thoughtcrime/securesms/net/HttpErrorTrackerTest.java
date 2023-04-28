package org.thoughtcrime.securesms.net;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HttpErrorTrackerTest {

  private static final long START_TIME       = TimeUnit.MINUTES.toMillis(60);
  private static final long SHORT_TIME       = TimeUnit.SECONDS.toMillis(1);
  private static final long LONG_TIME        = TimeUnit.SECONDS.toMillis(30);
  private static final long REALLY_LONG_TIME = TimeUnit.SECONDS.toMillis(90);

  @Test
  public void addSample() {
    HttpErrorTracker tracker = new HttpErrorTracker(2, TimeUnit.MINUTES.toMillis(1));
    // First sample
    assertFalse(tracker.addSample(START_TIME));
    // Second sample within 1 minute
    assertTrue(tracker.addSample(START_TIME + SHORT_TIME));
    // Reset, new first sample
    assertFalse(tracker.addSample(START_TIME + SHORT_TIME + LONG_TIME));
    // Second sample more than 1 minute after
    assertFalse(tracker.addSample(START_TIME + SHORT_TIME + LONG_TIME + REALLY_LONG_TIME));
  }
}
