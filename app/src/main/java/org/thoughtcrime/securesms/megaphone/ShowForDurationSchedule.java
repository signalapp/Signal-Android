package org.thoughtcrime.securesms.megaphone;

import java.util.concurrent.TimeUnit;

/**
 * Megaphone schedule that will always show for some duration after the first
 * time the user sees it.
 */
public class ShowForDurationSchedule implements MegaphoneSchedule {

  private final long duration;

  public static MegaphoneSchedule showForDays(int days) {
    return new ShowForDurationSchedule(TimeUnit.DAYS.toMillis(days));
  }

  public ShowForDurationSchedule(long duration) {
    this.duration = duration;
  }

  @Override
  public boolean shouldDisplay(int seenCount, long lastSeen, long firstVisible, long currentTime) {
    return firstVisible == 0 || currentTime < firstVisible + duration;
  }
}
