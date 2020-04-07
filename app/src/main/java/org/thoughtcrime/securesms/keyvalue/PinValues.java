package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.lock.SignalPinReminders;
import org.thoughtcrime.securesms.lock.v2.PinKeyboardType;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

/**
 * Specifically handles just the UI/UX state around PINs. For actual keys, see {@link KbsValues}.
 */
public final class PinValues {

  private static final String TAG = Log.tag(PinValues.class);

  private static final String LAST_SUCCESSFUL_ENTRY = "pin.last_successful_entry";
  private static final String NEXT_INTERVAL         = "pin.interval_index";
  private static final String KEYBOARD_TYPE         = "kbs.keyboard_type";
  private static final String PIN_STATE             = "pin.pin_state";

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

  public void resetPinReminders() {
    long nextInterval = SignalPinReminders.INITIAL_INTERVAL;
    Log.i(TAG, "resetPinReminders() nextInterval: " + nextInterval, new Throwable());

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

  public void setKeyboardType(@NonNull PinKeyboardType keyboardType) {
    store.beginWrite()
         .putString(KEYBOARD_TYPE, keyboardType.getCode())
         .commit();
  }

  public @NonNull PinKeyboardType getKeyboardType() {
    return PinKeyboardType.fromCode(store.getString(KEYBOARD_TYPE, null));
  }

  /** Should only be set by {@link org.thoughtcrime.securesms.pin.PinState} */
  public void setPinState(@NonNull String pinState) {
    store.beginWrite().putString(PIN_STATE, pinState).commit();
  }

  public @Nullable String getPinState() {
    return store.getString(PIN_STATE, null);
  }
}
