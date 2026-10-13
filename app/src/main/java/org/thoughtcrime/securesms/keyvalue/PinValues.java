package org.thoughtcrime.securesms.keyvalue;

import android.content.Context;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.lock.RegistrationLockReminders;
import org.thoughtcrime.securesms.lock.SignalPinReminders;
import org.thoughtcrime.securesms.lock.v2.PinKeyboardType;

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
  private static final String MIGRATION_VERSION     = "pin.migration_version";
  public  static final String PIN_REMINDERS_ENABLED = "pin.pin_reminders_enabled";

  private static final int CURRENT_MIGRATION_VERSION = 1;

  PinValues(KeyValueStore store, @NonNull Context context) {
    super(store);

    if (getLong(MIGRATION_VERSION, 0) < CURRENT_MIGRATION_VERSION) {
      migrateFromSharedPrefsV1(context);
    }
  }

  /**
   * Unlike the other stores, we can't use one of the migrated keys as the "has this run?" marker: both {@link #NEXT_INTERVAL} and
   * {@link #LAST_SUCCESSFUL_ENTRY} are keys released versions already write, so their presence says nothing about whether this
   * migration ran. Hence the explicit {@link #MIGRATION_VERSION}.
   *
   * These values used to be read straight out of shared prefs as the *default* for the keys above, so only migrate the ones this
   * store doesn't already have a value for.
   *
   * Do not alter. If you need to migrate more stuff, bump {@link #CURRENT_MIGRATION_VERSION} and add a new method.
   */
  private void migrateFromSharedPrefsV1(@NonNull Context context) {
    Log.i(TAG, "[V1] Migrating pin values from shared prefs.");

    KeyValueStore.Writer writer = getStore().beginWrite();

    if (!getStore().containsKey(LAST_SUCCESSFUL_ENTRY)) {
      writer.putLong(LAST_SUCCESSFUL_ENTRY, LegacySharedPrefs.INSTANCE.getLong(context, "pref_registration_lock_last_reminder_time_post_kbs", 0));
    }

    if (!getStore().containsKey(NEXT_INTERVAL)) {
      writer.putLong(NEXT_INTERVAL, LegacySharedPrefs.INSTANCE.getLong(context, "pref_registration_lock_next_reminder_interval", RegistrationLockReminders.INITIAL_INTERVAL));
    }

    writer.putLong(MIGRATION_VERSION, CURRENT_MIGRATION_VERSION)
          .commit();
  }

  @Override
  void onFirstEverAppLaunch() {
  }

  @Override
  @NonNull List<String> getKeysToIncludeInBackup() {
    return Arrays.asList(PIN_REMINDERS_ENABLED, KEYBOARD_TYPE);
  }

  public void onEntrySuccess(@NonNull String pin) {
    long nextInterval = SignalPinReminders.getNextInterval(getNextReminderInterval());
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
    long nextInterval = SignalPinReminders.getPreviousInterval(getNextReminderInterval());
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
      nextInterval = SignalPinReminders.getPreviousInterval(getNextReminderInterval());
    } else {
      nextInterval = getNextReminderInterval();
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

  public long getNextReminderInterval() {
    return getLong(NEXT_INTERVAL, RegistrationLockReminders.INITIAL_INTERVAL);
  }

  public void setNextReminderInterval(long interval) {
    putLong(NEXT_INTERVAL, interval);
  }

  public long getLastSuccessfulEntryTime() {
    return getLong(LAST_SUCCESSFUL_ENTRY, 0);
  }

  public void setLastSuccessfulEntryTime(long time) {
    putLong(LAST_SUCCESSFUL_ENTRY, time);
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
