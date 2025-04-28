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
  private static final String LAST_REMINDER_TIME    = "pin.last_reminder_time";
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

    long now = System.currentTimeMillis();

    getStore().beginWrite()
              .putLong(LAST_SUCCESSFUL_ENTRY, now)
              .putLong(NEXT_INTERVAL, nextInterval)
              .putLong(LAST_REMINDER_TIME, now)
              .apply();

    SignalStore.svr().setPinIfNotPresent(pin);
  }

  public void onEntrySuccessWithWrongGuess(@NonNull String pin) {
    long nextInterval = SignalPinReminders.getPreviousInterval(getCurrentInterval());
    Log.i(TAG, "onEntrySuccessWithWrongGuess() nextInterval: " + nextInterval);

    long now = System.currentTimeMillis();

    getStore().beginWrite()
              .putLong(LAST_SUCCESSFUL_ENTRY, now)
              .putLong(NEXT_INTERVAL, nextInterval)
              .putLong(LAST_REMINDER_TIME, now)
              .apply();

    SignalStore.svr().setPinIfNotPresent(pin);
  }

  /**
   * Updates LAST_REMINDER_TIME and in the case of a failed guess, ratches
   * back the interval until next reminder.
   */
  public void onEntrySkip(boolean includedFailure) {
    long nextInterval;

    if (includedFailure) {
      nextInterval = SignalPinReminders.getPreviousInterval(getCurrentInterval());
    } else {
      nextInterval = getCurrentInterval();
    }

    Log.i(TAG, "onEntrySkip(includedFailure: " + includedFailure +") nextInterval: " + nextInterval);

    getStore().beginWrite()
              .putLong(NEXT_INTERVAL, nextInterval)
              .putLong(LAST_REMINDER_TIME, System.currentTimeMillis())
              .apply();
  }

  public void resetPinReminders() {
    long nextInterval = SignalPinReminders.INITIAL_INTERVAL;
    Log.i(TAG, "resetPinReminders() nextInterval: " + nextInterval, new Throwable());

    long now = System.currentTimeMillis();

    getStore().beginWrite()
              .putLong(NEXT_INTERVAL, nextInterval)
              .putLong(LAST_SUCCESSFUL_ENTRY, now)
              .putLong(LAST_REMINDER_TIME, now)
              .apply();
  }

  public long getCurrentInterval() {
    return getLong(NEXT_INTERVAL, TextSecurePreferences.getRegistrationLockNextReminderInterval(AppDependencies.getApplication()));
  }

  public long getLastSuccessfulEntryTime() {
    return getLong(LAST_SUCCESSFUL_ENTRY, TextSecurePreferences.getRegistrationLockLastReminderTime(AppDependencies.getApplication()));
  }

  public long getLastReminderTime() {
    return getLong(LAST_REMINDER_TIME, getLastSuccessfulEntryTime());
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
