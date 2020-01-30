package org.thoughtcrime.securesms.megaphone;

import androidx.annotation.VisibleForTesting;

import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.Util;

import java.util.concurrent.TimeUnit;

class PinsForAllSchedule implements MegaphoneSchedule {

  @VisibleForTesting
  static final long DAYS_UNTIL_FULLSCREEN = 8L;

  @VisibleForTesting
  static final long DAYS_REMAINING_MAX    = DAYS_UNTIL_FULLSCREEN - 1;

  private final MegaphoneSchedule schedule = new RecurringSchedule(TimeUnit.DAYS.toMillis(2));
  private final boolean           enabled  = !SignalStore.registrationValues().isPinRequired() || FeatureFlags.pinsForAll();

  static boolean shouldDisplayFullScreen(long firstVisible, long currentTime) {
    if (firstVisible == 0L) {
      return false;
    } else {
      return currentTime - firstVisible >= TimeUnit.DAYS.toMillis(DAYS_UNTIL_FULLSCREEN);
    }
  }

  static long getDaysRemaining(long firstVisible, long currentTime) {
    if (firstVisible == 0L) {
      return DAYS_REMAINING_MAX;
    } else {
      return Util.clamp(DAYS_REMAINING_MAX - TimeUnit.MILLISECONDS.toDays(currentTime - firstVisible), 0, DAYS_REMAINING_MAX);
    }
  }

  @Override
  public boolean shouldDisplay(int seenCount, long lastSeen, long firstVisible, long currentTime) {
    if (!enabled) return false;

    if (shouldDisplayFullScreen(firstVisible, currentTime)) {
      return true;
    } else {
      return schedule.shouldDisplay(seenCount, lastSeen, firstVisible, currentTime);
    }
  }
}
