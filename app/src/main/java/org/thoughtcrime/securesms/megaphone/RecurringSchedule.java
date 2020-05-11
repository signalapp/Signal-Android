package org.thoughtcrime.securesms.megaphone;

class RecurringSchedule implements MegaphoneSchedule {

  private final long[] gaps;

  RecurringSchedule(long... durationGaps) {
    this.gaps = durationGaps;
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
