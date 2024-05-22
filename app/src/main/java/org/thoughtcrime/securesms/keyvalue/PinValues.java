package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.lock.SignalPinReminders;
import org.thoughtcrime.securesms.lock.v2.PinKeyboardType;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.util.Arrays;
import java.util.List;

/**
 * Specifically handles just the UI/UX state around PINs. For actual keys, see {@link SvrValues}.
 */
public final class PinValues extends SignalStoreValues {

  private static final String TAG = Log.tag(PinValues.class);

  private static final String LAST_SUCCESSFUL_ENTRY = "pin.last_successful_entry";
  private static final String NEXT_INTERVAL         = "pin.interval_index";
  private static final String KEYBOARD_TYPE         = "kbs.keyboard_type";
  public  static final String PIN_REMINDERS_ENABLED = "pin.pin_reminders_enabled";

  PinValues(KeyValueStore store) {
    super(store);
  }

  @Override
  void onFirstEverAppLaunch() {
  }

  @Override
  @NonNull List<String> getKeysToIncludeInBackup() {
    return Arrays.asList(PIN_REMINDERS_ENABLED, KEYBOARD_TYPE);
  }

  public void onEntrySuccess(@NonNull String pin) {
    long nextInterval = SignalPinReminders.getNextInterval(getCurrentInterval());
    Log.i(TAG, "onEntrySuccess() nextInterval: " + nextInterval);

    getStore().beginWrite()
              .putLong(LAST_SUCCESSFUL_ENTRY, System.currentTimeMillis())
              .putLong(NEXT_INTERVAL, nextInterval)
              .apply();

    SignalStore.svr().setPinIfNotPresent(pin);
  }

  public void onEntrySuccessWithWrongGuess(@NonNull String pin) {
    long nextInterval = SignalPinReminders.getPreviousInterval(getCurrentInterval());
    Log.i(TAG, "onEntrySuccessWithWrongGuess() nextInterval: " + nextInterval);

    getStore().beginWrite()
              .putLong(LAST_SUCCESSFUL_ENTRY, System.currentTimeMillis())
              .putLong(NEXT_INTERVAL, nextInterval)
              .apply();

    SignalStore.svr().setPinIfNotPresent(pin);
  }

  public void onEntrySkipWithWrongGuess() {
    long nextInterval = SignalPinReminders.getPreviousInterval(getCurrentInterval());
    Log.i(TAG, "onEntrySkipWithWrongGuess() nextInterval: " + nextInterval);

    putLong(NEXT_INTERVAL, nextInterval);
  }

  public void resetPinReminders() {
    long nextInterval = SignalPinReminders.INITIAL_INTERVAL;
    Log.i(TAG, "resetPinReminders() nextInterval: " + nextInterval, new Throwable());

    getStore().beginWrite()
              .putLong(NEXT_INTERVAL, nextInterval)
              .putLong(LAST_SUCCESSFUL_ENTRY, System.currentTimeMillis())
              .apply();
  }

  public long getCurrentInterval() {
    return getLong(NEXT_INTERVAL, TextSecurePreferences.getRegistrationLockNextReminderInterval(AppDependencies.getApplication()));
  }

  public long getLastSuccessfulEntryTime() {
    return getLong(LAST_SUCCESSFUL_ENTRY, TextSecurePreferences.getRegistrationLockLastReminderTime(AppDependencies.getApplication()));
  }

  public void setKeyboardType(@NonNull PinKeyboardType keyboardType) {
    putString(KEYBOARD_TYPE, keyboardType.getCode());
  }

  public void setPinRemindersEnabled(boolean enabled) {
    putBoolean(PIN_REMINDERS_ENABLED, enabled);
  }

  public boolean arePinRemindersEnabled() {
    return getBoolean(PIN_REMINDERS_ENABLED, true);
  }

  public @NonNull PinKeyboardType getKeyboardType() {
    String pin = SignalStore.svr().getPin();

    if (pin == null) {
      return PinKeyboardType.fromCode(getStore().getString(KEYBOARD_TYPE, null));
    }

    for (char c : pin.toCharArray()) {
      if (!Character.isDigit(c)) {
        return PinKeyboardType.ALPHA_NUMERIC;
      }
    }

    return PinKeyboardType.NUMERIC;
  }

  public void setNextReminderIntervalToAtMost(long maxInterval) {
    if (getStore().getLong(NEXT_INTERVAL, 0) > maxInterval) {
      putLong(NEXT_INTERVAL, maxInterval);
    }
  }
}
