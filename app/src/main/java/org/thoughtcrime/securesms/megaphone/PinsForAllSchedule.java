package org.thoughtcrime.securesms.megaphone;

import androidx.annotation.VisibleForTesting;

import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

class PinsForAllSchedule implements MegaphoneSchedule {

  private static final String TAG = Log.tag(PinsForAllSchedule.class);

  @VisibleForTesting
  static final long DAYS_UNTIL_FULLSCREEN = 8L;

  private final MegaphoneSchedule schedule = new RecurringSchedule(TimeUnit.HOURS.toMillis(2));

  static boolean shouldDisplayFullScreen(long firstVisible, long currentTime) {
    if (!FeatureFlags.pinsForAllMandatory()) {
      return false;
    }

    if (firstVisible == 0L) {
      return false;
    }

    return currentTime - firstVisible >= TimeUnit.DAYS.toMillis(DAYS_UNTIL_FULLSCREEN);
  }

  @Override
  public boolean shouldDisplay(int seenCount, long lastSeen, long firstVisible, long currentTime) {
    if (!isEnabled()) {
      return false;
    }

    if (shouldDisplayFullScreen(firstVisible, currentTime)) {
      return true;
    } else {
      boolean shouldDisplay = schedule.shouldDisplay(seenCount, lastSeen, firstVisible, currentTime);
      Log.i(TAG, String.format(Locale.ENGLISH, "seenCount: %d,  lastSeen: %d,  firstVisible: %d,  currentTime: %d, result: %b", seenCount, lastSeen, firstVisible, currentTime, shouldDisplay));
      return shouldDisplay;
    }
  }

  private static boolean isEnabled() {
    if (SignalStore.kbsValues().hasPin()) {
      return false;
    }

    if (FeatureFlags.pinsForAllMegaphoneKillSwitch()) {
      return false;
    }

    if (pinCreationFailedDuringRegistration()) {
      return true;
    }

    if (newlyRegisteredRegistrationLockV1User()) {
      return true;
    }

    if (SignalStore.registrationValues().pinWasRequiredAtRegistration()) {
      return false;
    }

    return true;
  }

  private static boolean pinCreationFailedDuringRegistration() {
    return SignalStore.registrationValues().pinWasRequiredAtRegistration() &&
           !SignalStore.kbsValues().hasPin()                               &&
           !TextSecurePreferences.isV1RegistrationLockEnabled(ApplicationDependencies.getApplication());
  }

  private static boolean newlyRegisteredRegistrationLockV1User() {
    return SignalStore.registrationValues().pinWasRequiredAtRegistration() && TextSecurePreferences.isV1RegistrationLockEnabled(ApplicationDependencies.getApplication());
  }
}
