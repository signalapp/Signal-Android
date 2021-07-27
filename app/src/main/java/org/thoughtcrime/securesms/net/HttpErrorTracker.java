package org.thoughtcrime.securesms.net;

import java.util.Arrays;

/**
 * Track error occurrences by time and indicate if too many occur within the
 * time limit.
 */
public final class HttpErrorTracker {

  private final long[] timestamps;
  private final long   errorTimeRange;

  public HttpErrorTracker(int samples, long errorTimeRange) {
    this.timestamps     = new long[samples];
    this.errorTimeRange = errorTimeRange;
  }

  public synchronized boolean addSample(long now) {
    long errorsMustBeAfter = now - errorTimeRange;
    int  count             = 1;
    int  minIndex          = 0;

    for (int i = 0; i < timestamps.length; i++) {
      if (timestamps[i] < errorsMustBeAfter) {
        timestamps[i] = 0;
      } else if (timestamps[i] != 0) {
        count++;
      }

      if (timestamps[i] < timestamps[minIndex]) {
        minIndex = i;
      }
    }

    timestamps[minIndex] = now;

    if (count >= timestamps.length) {
      Arrays.fill(timestamps, 0);
      return true;
    }
    return false;
  }
}
