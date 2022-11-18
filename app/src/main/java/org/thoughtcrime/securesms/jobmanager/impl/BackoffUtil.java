package org.thoughtcrime.securesms.jobmanager.impl;

public final class BackoffUtil {

  private BackoffUtil() {}

  /**
   * Simple exponential backoff with random jitter.
   * @param pastAttemptCount The number of attempts that have already been made.
   *
   * @return The calculated backoff.
   */
  public static long exponentialBackoff(int pastAttemptCount, long maxBackoff) {
    if (pastAttemptCount < 1) {
      throw new IllegalArgumentException("Bad attempt count! " + pastAttemptCount);
    }

    int    boundedAttempt     = Math.min(pastAttemptCount, 30);
    long   exponentialBackoff = (long) Math.pow(2, boundedAttempt) * 1000;
    long   actualBackoff      = Math.min(exponentialBackoff, maxBackoff);
    double jitter             = 0.75 + (Math.random() * 0.5);

    return (long) (actualBackoff * jitter);
  }
}
