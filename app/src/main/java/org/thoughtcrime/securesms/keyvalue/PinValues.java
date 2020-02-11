package org.thoughtcrime.securesms.keyvalue;

import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.lock.SignalPinReminders;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

public final class PinValues {

  private static final String TAG = Log.tag(PinValues.class);

  private static final String LAST_SUCCESSFUL_ENTRY = "pin.last_successful_entry";
  private static final String NEXT_INTERVAL         = "pin.interval_index";

  private final KeyValueStore store;

  PinValues(KeyValueStore store) {
    this.store = store;
  }

  public void onEntrySuccess() {
    long nextInterval = SignalPinReminders.getNextInterval(getCurrentInterval());
    Log.i(TAG, "onEntrySuccess() nextInterval: " + nextInterval);

    store.beginWrite()
         .putLong(LAST_SUCCESSFUL_ENTRY, System.currentTimeMillis())
         .putLong(NEXT_INTERVAL, nextInterval)
         .apply();
  }

  public void onEntrySuccessWithWrongGuess() {
    long nextInterval = SignalPinReminders.getPreviousInterval(getCurrentInterval());
    Log.i(TAG, "onEntrySuccessWithWrongGuess() nextInterval: " + nextInterval);

    store.beginWrite()
         .putLong(LAST_SUCCESSFUL_ENTRY, System.currentTimeMillis())
         .putLong(NEXT_INTERVAL, nextInterval)
         .apply();
  }

  public void onEntrySkipWithWrongGuess() {
    long nextInterval = SignalPinReminders.getPreviousInterval(getCurrentInterval());
    Log.i(TAG, "onEntrySkipWithWrongGuess() nextInterval: " + nextInterval);

    store.beginWrite()
         .putLong(NEXT_INTERVAL, nextInterval)
         .apply();
  }


  public void onPinChange() {
    long nextInterval = SignalPinReminders.INITIAL_INTERVAL;
    Log.i(TAG, "onPinChange() nextInterval: " + nextInterval);

    store.beginWrite()
         .putLong(NEXT_INTERVAL, nextInterval)
         .putLong(LAST_SUCCESSFUL_ENTRY, System.currentTimeMillis())
         .apply();
  }

  public long getCurrentInterval() {
    return store.getLong(NEXT_INTERVAL, TextSecurePreferences.getRegistrationLockNextReminderInterval(ApplicationDependencies.getApplication()));
  }

  public long getLastSuccessfulEntryTime() {
    return store.getLong(LAST_SUCCESSFUL_ENTRY, TextSecurePreferences.getRegistrationLockLastReminderTime(ApplicationDependencies.getApplication()));
  }
}
