package org.thoughtcrime.securesms.megaphone;

import androidx.annotation.NonNull;

/**
 * A schedule that provides a high level of control, allowing you to specify an amount of time to
 * wait based on how many times a user has seen the megaphone.
 */
class RecurringSchedule implements MegaphoneSchedule {

  private final long[] gaps;

  /**
   * How long to wait after each time a user has seen the megaphone. Index 0 corresponds to how long
   * to wait to show it again after the user has seen it once, index 1 is for after the user has
   * seen it twice, etc. If the seen count is greater than the number of provided intervals, it will
   * continue to use the last interval provided indefinitely.
   *
   * The schedule will always show the megaphone if the user has never seen it.
   */
  RecurringSchedule(long... durationGaps) {
    this.gaps = durationGaps;
  }

  /**
   * Shortcut for a recurring schedule with a single interval.
   */
  public static @NonNull MegaphoneSchedule every(long interval) {
    return new RecurringSchedule(interval);
  }

  @Override
  public boolean shouldDisplay(int seenCount, long lastSeen, long firstVisible, long currentTime) {
    if (seenCount == 0) {
      return true;
    }

    long gap = gaps[Math.min(seenCount - 1, gaps.length - 1)];

    return lastSeen + gap <= currentTime ;
  }
}
