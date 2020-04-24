package org.thoughtcrime.securesms.pin;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.JobTracker;
import org.thoughtcrime.securesms.jobs.RefreshAttributesJob;
import org.thoughtcrime.securesms.keyvalue.KbsValues;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.lock.PinHashing;
import org.thoughtcrime.securesms.lock.RegistrationLockReminders;
import org.thoughtcrime.securesms.lock.v2.PinKeyboardType;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.megaphone.Megaphones;
import org.thoughtcrime.securesms.registration.service.KeyBackupSystemWrongPinException;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.KbsPinData;
import org.whispersystems.signalservice.api.KeyBackupService;
import org.whispersystems.signalservice.api.KeyBackupServicePinException;
import org.whispersystems.signalservice.api.KeyBackupSystemNoDataException;
import org.whispersystems.signalservice.api.kbs.HashedPin;
import org.whispersystems.signalservice.api.kbs.MasterKey;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedResponseException;
import org.whispersystems.signalservice.internal.contacts.entities.TokenResponse;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class PinState {

  private static final String TAG = Log.tag(PinState.class);

  /**
   * Invoked during registration to restore the master key based on the server response during
   * verification.
   *
   * Does not affect {@link PinState}.
   */
  public static synchronized @Nullable KbsPinData restoreMasterKey(@Nullable String pin,
                                                                   @Nullable String basicStorageCredentials,
                                                                   @NonNull TokenResponse tokenResponse)
    throws IOException, KeyBackupSystemWrongPinException, KeyBackupSystemNoDataException
  {
    Log.i(TAG, "restoreMasterKey()");

    if (pin == null) return null;

    if (basicStorageCredentials == null) {
      throw new AssertionError("Cannot restore KBS key, no storage credentials supplied");
    }

    KeyBackupService keyBackupService = ApplicationDependencies.getKeyBackupService();

    Log.i(TAG, "Opening key backup service session");
    KeyBackupService.RestoreSession session = keyBackupService.newRegistrationSession(basicStorageCredentials, tokenResponse);

    try {
      Log.i(TAG, "Restoring pin from KBS");
      HashedPin  hashedPin = PinHashing.hashPin(pin, session);
      KbsPinData kbsData   = session.restorePin(hashedPin);
      if (kbsData != null) {
        Log.i(TAG, "Found registration lock token on KBS.");
      } else {
        throw new AssertionError("Null not expected");
      }
      return kbsData;
    } catch (UnauthenticatedResponseException e) {
      Log.w(TAG, "Failed to restore key", e);
      throw new IOException(e);
    } catch (KeyBackupServicePinException e) {
      Log.w(TAG, "Incorrect pin", e);
      throw new KeyBackupSystemWrongPinException(e.getToken());
    }
  }

  /**
   * Invoked after a user has successfully registered. Ensures all the necessary state is updated.
   */
  public static synchronized void onRegistration(@NonNull Context context,
                                                 @Nullable KbsPinData kbsData,
                                                 @Nullable String pin,
                                                 boolean hasPinToRestore)
  {
    Log.i(TAG, "onNewRegistration()");

    TextSecurePreferences.setV1RegistrationLockPin(context, pin);

    if (kbsData == null && pin != null) {
      Log.i(TAG, "Registration Lock V1");
      SignalStore.kbsValues().clearRegistrationLockAndPin();
      TextSecurePreferences.setV1RegistrationLockEnabled(context, true);
      TextSecurePreferences.setRegistrationLockLastReminderTime(context, System.currentTimeMillis());
      TextSecurePreferences.setRegistrationLockNextReminderInterval(context, RegistrationLockReminders.INITIAL_INTERVAL);
    } else if (kbsData != null && pin != null) {
      Log.i(TAG, "Registration Lock V2");
      TextSecurePreferences.setV1RegistrationLockEnabled(context, false);
      SignalStore.kbsValues().setV2RegistrationLockEnabled(true);
      SignalStore.kbsValues().setKbsMasterKey(kbsData, PinHashing.localPinHash(pin));
      SignalStore.pinValues().resetPinReminders();
      resetPinRetryCount(context, pin, kbsData);
    } else if (hasPinToRestore) {
      Log.i(TAG, "Has a PIN to restore.");
      SignalStore.kbsValues().clearRegistrationLockAndPin();
      TextSecurePreferences.setV1RegistrationLockEnabled(context, false);
      SignalStore.storageServiceValues().setNeedsAccountRestore(true);
    } else {
      Log.i(TAG, "No registration lock or PIN at all.");
      SignalStore.kbsValues().clearRegistrationLockAndPin();
      TextSecurePreferences.setV1RegistrationLockEnabled(context, false);
    }

    updateState(buildInferredStateFromOtherFields());
  }

  /**
   * Invoked when the user is going through the PIN restoration flow (which is separate from reglock).
   */
  public static synchronized void onSignalPinRestore(@NonNull Context context, @NonNull KbsPinData kbsData, @NonNull String pin) {
    SignalStore.kbsValues().setKbsMasterKey(kbsData, PinHashing.localPinHash(pin));
    SignalStore.kbsValues().setV2RegistrationLockEnabled(false);
    SignalStore.pinValues().resetPinReminders();
    SignalStore.storageServiceValues().setNeedsAccountRestore(false);
    resetPinRetryCount(context, pin, kbsData);

    updateState(buildInferredStateFromOtherFields());
  }

  /**
   * Invoked when the user skips out on PIN restoration or otherwise fails to remember their PIN.
   */
  public static synchronized void onPinRestoreForgottenOrSkipped() {
    SignalStore.kbsValues().clearRegistrationLockAndPin();
    SignalStore.storageServiceValues().setNeedsAccountRestore(false);

    updateState(buildInferredStateFromOtherFields());
  }

  /**
   * Invoked whenever the Signal PIN is changed or created.
   */
  @WorkerThread
  public static synchronized void onPinChangedOrCreated(@NonNull Context context, @NonNull String pin, @NonNull PinKeyboardType keyboard)
      throws IOException, UnauthenticatedResponseException
  {
    Log.i(TAG, "onPinChangedOrCreated()");

    KbsValues                         kbsValues        = SignalStore.kbsValues();
    boolean                           isFirstPin       = !kbsValues.hasPin();
    MasterKey                         masterKey        = kbsValues.getOrCreateMasterKey();
    KeyBackupService                  keyBackupService = ApplicationDependencies.getKeyBackupService();
    KeyBackupService.PinChangeSession pinChangeSession = keyBackupService.newPinChangeSession();
    HashedPin                         hashedPin        = PinHashing.hashPin(pin, pinChangeSession);
    KbsPinData                        kbsData          = pinChangeSession.setPin(hashedPin, masterKey);

    kbsValues.setKbsMasterKey(kbsData, PinHashing.localPinHash(pin));
    TextSecurePreferences.clearRegistrationLockV1(context);
    SignalStore.pinValues().setKeyboardType(keyboard);
    SignalStore.pinValues().resetPinReminders();
    ApplicationDependencies.getMegaphoneRepository().markFinished(Megaphones.Event.PINS_FOR_ALL);

    if (isFirstPin) {
      Log.i(TAG, "First time setting a PIN. Refreshing attributes to set the 'storage' capability.");
      bestEffortRefreshAttributes();
    } else {
      Log.i(TAG, "Not the first time setting a PIN.");
    }

    updateState(buildInferredStateFromOtherFields());
  }

  /**
   * Invoked whenever a Signal PIN user enables registration lock.
   */
  @WorkerThread
  public static synchronized void onEnableRegistrationLockForUserWithPin() throws IOException {
    Log.i(TAG, "onEnableRegistrationLockForUserWithPin()");

    if (getState() == State.PIN_WITH_REGISTRATION_LOCK_ENABLED) {
      Log.i(TAG, "Registration lock already enabled. Skipping.");
      return;
    }

    assertState(State.PIN_WITH_REGISTRATION_LOCK_DISABLED);

    SignalStore.kbsValues().setV2RegistrationLockEnabled(false);
    ApplicationDependencies.getKeyBackupService()
                           .newPinChangeSession(SignalStore.kbsValues().getRegistrationLockTokenResponse())
                           .enableRegistrationLock(SignalStore.kbsValues().getOrCreateMasterKey());
    SignalStore.kbsValues().setV2RegistrationLockEnabled(true);

    updateState(State.PIN_WITH_REGISTRATION_LOCK_ENABLED);
  }

  /**
   * Invoked whenever a Signal PIN user disables registration lock.
   */
  @WorkerThread
  public static synchronized void onDisableRegistrationLockForUserWithPin() throws IOException {
    Log.i(TAG, "onDisableRegistrationLockForUserWithPin()");

    if (getState() == State.PIN_WITH_REGISTRATION_LOCK_DISABLED) {
      Log.i(TAG, "Registration lock already disabled. Skipping.");
      return;
    }

    assertState(State.PIN_WITH_REGISTRATION_LOCK_ENABLED);

    SignalStore.kbsValues().setV2RegistrationLockEnabled(true);
    ApplicationDependencies.getKeyBackupService()
                           .newPinChangeSession(SignalStore.kbsValues().getRegistrationLockTokenResponse())
                           .disableRegistrationLock();
    SignalStore.kbsValues().setV2RegistrationLockEnabled(false);

    updateState(State.PIN_WITH_REGISTRATION_LOCK_DISABLED);
  }

  /**
   * Called when registration lock is disabled in the settings using the old UI (i.e. no mention of
   * Signal PINs).
   */
  @WorkerThread
  public static synchronized void onDisableLegacyRegistrationLockPreference(@NonNull Context context)
      throws IOException, UnauthenticatedResponseException
  {
    Log.i(TAG, "onDisableRegistrationLockV1()");
    assertState(State.REGISTRATION_LOCK_V1);

    Log.i(TAG, "Removing v1 registration lock pin from server");
    ApplicationDependencies.getSignalServiceAccountManager().removeRegistrationLockV1();
    TextSecurePreferences.clearRegistrationLockV1(context);

    updateState(State.NO_REGISTRATION_LOCK);
  }

  /**
   * Called when registration lock is enabled in the settings using the old UI (i.e. no mention of
   * Signal PINs).
   */
  @WorkerThread
  public static synchronized void onEnableLegacyRegistrationLockPreference(@NonNull Context context, @NonNull String pin)
      throws IOException, UnauthenticatedResponseException
  {
    Log.i(TAG, "onCompleteRegistrationLockV1Reminder()");
    assertState(State.NO_REGISTRATION_LOCK);

    KbsValues                         kbsValues        = SignalStore.kbsValues();
    MasterKey                         masterKey        = kbsValues.getOrCreateMasterKey();
    KeyBackupService                  keyBackupService = ApplicationDependencies.getKeyBackupService();
    KeyBackupService.PinChangeSession pinChangeSession = keyBackupService.newPinChangeSession();
    HashedPin                         hashedPin        = PinHashing.hashPin(pin, pinChangeSession);
    KbsPinData                        kbsData          = pinChangeSession.setPin(hashedPin, masterKey);

    pinChangeSession.enableRegistrationLock(masterKey);

    kbsValues.setKbsMasterKey(kbsData, PinHashing.localPinHash(pin));
    kbsValues.setV2RegistrationLockEnabled(true);
    TextSecurePreferences.clearRegistrationLockV1(context);
    TextSecurePreferences.setRegistrationLockLastReminderTime(context, System.currentTimeMillis());
    TextSecurePreferences.setRegistrationLockNextReminderInterval(context, RegistrationLockReminders.INITIAL_INTERVAL);

    updateState(buildInferredStateFromOtherFields());
  }

  /**
   * Should only be called by {@link org.thoughtcrime.securesms.migrations.RegistrationPinV2MigrationJob}.
   */
  @WorkerThread
  public static synchronized void onMigrateToRegistrationLockV2(@NonNull Context context, @NonNull String pin)
      throws IOException, UnauthenticatedResponseException
  {
    KbsValues                         kbsValues        = SignalStore.kbsValues();
    MasterKey                         masterKey        = kbsValues.getOrCreateMasterKey();
    KeyBackupService                  keyBackupService = ApplicationDependencies.getKeyBackupService();
    KeyBackupService.PinChangeSession pinChangeSession = keyBackupService.newPinChangeSession();
    HashedPin                         hashedPin        = PinHashing.hashPin(pin, pinChangeSession);
    KbsPinData                        kbsData          = pinChangeSession.setPin(hashedPin, masterKey);

    pinChangeSession.enableRegistrationLock(masterKey);

    kbsValues.setKbsMasterKey(kbsData, PinHashing.localPinHash(pin));
    TextSecurePreferences.clearRegistrationLockV1(context);

    updateState(buildInferredStateFromOtherFields());
  }

  public static synchronized boolean shouldShowRegistrationLockV1Reminder() {
    return getState() == State.REGISTRATION_LOCK_V1;
  }

  @WorkerThread
  private static void bestEffortRefreshAttributes() {
    Optional<JobTracker.JobState> result = ApplicationDependencies.getJobManager().runSynchronously(new RefreshAttributesJob(), TimeUnit.SECONDS.toMillis(10));

    if (result.isPresent() && result.get() == JobTracker.JobState.SUCCESS) {
      Log.w(TAG, "Attributes were refreshed successfully.");
    } else if (result.isPresent()) {
      Log.w(TAG, "Attribute refresh finished, but was not successful. Enqueuing one for later. (Result: " + result.get() + ")");
      ApplicationDependencies.getJobManager().add(new RefreshAttributesJob());
    } else {
      Log.w(TAG, "Job did not finish in the allotted time. It'll finish later.");
    }
  }

  @WorkerThread
  private static void resetPinRetryCount(@NonNull Context context, @Nullable String pin, @NonNull KbsPinData kbsData) {
    if (pin == null) {
      return;
    }

    KeyBackupService keyBackupService = ApplicationDependencies.getKeyBackupService();

    try {
      KbsValues                         kbsValues        = SignalStore.kbsValues();
      MasterKey                         masterKey        = kbsValues.getOrCreateMasterKey();
      KeyBackupService.PinChangeSession pinChangeSession = keyBackupService.newPinChangeSession(kbsData.getTokenResponse());
      HashedPin                         hashedPin        = PinHashing.hashPin(pin, pinChangeSession);
      KbsPinData                        newData          = pinChangeSession.setPin(hashedPin, masterKey);

      kbsValues.setKbsMasterKey(newData, PinHashing.localPinHash(pin));
      TextSecurePreferences.clearRegistrationLockV1(context);
    } catch (IOException e) {
      Log.w(TAG, "May have failed to reset pin attempts!", e);
    } catch (UnauthenticatedResponseException e) {
      Log.w(TAG, "Failed to reset pin attempts", e);
    }
  }

  private static @NonNull State assertState(State... allowed) {
    State currentState = getState();

    for (State state : allowed) {
      if (currentState == state) {
        return currentState;
      }
    }

    switch (currentState) {
      case NO_REGISTRATION_LOCK:                throw new InvalidState_NoRegistrationLock();
      case REGISTRATION_LOCK_V1:                throw new InvalidState_RegistrationLockV1();
      case PIN_WITH_REGISTRATION_LOCK_ENABLED:  throw new InvalidState_PinWithRegistrationLockEnabled();
      case PIN_WITH_REGISTRATION_LOCK_DISABLED: throw new InvalidState_PinWithRegistrationLockDisabled();
      default:                                  throw new IllegalStateException("Expected: " + Arrays.toString(allowed) + ", Actual: " + currentState);
    }
  }

  private static @NonNull State getState() {
    String serialized = SignalStore.pinValues().getPinState();

    if (serialized != null) {
      return State.deserialize(serialized);
    } else {
      State state = buildInferredStateFromOtherFields();
      SignalStore.pinValues().setPinState(state.serialize());
      return state;
    }
  }

  private static void updateState(@NonNull State state) {
    Log.i(TAG, "Updating state to: " + state);
    SignalStore.pinValues().setPinState(state.serialize());
  }

  private static @NonNull State buildInferredStateFromOtherFields() {
    Context   context   = ApplicationDependencies.getApplication();
    KbsValues kbsValues = SignalStore.kbsValues();

    boolean v1Enabled = TextSecurePreferences.isV1RegistrationLockEnabled(context);
    boolean v2Enabled = kbsValues.isV2RegistrationLockEnabled();
    boolean hasPin    = kbsValues.hasPin();

    if (!v1Enabled && !v2Enabled && !hasPin) {
      return State.NO_REGISTRATION_LOCK;
    }

    if (v1Enabled && !v2Enabled && !hasPin) {
      return State.REGISTRATION_LOCK_V1;
    }

    if (v2Enabled && hasPin) {
      TextSecurePreferences.setV1RegistrationLockEnabled(context, false);
      return State.PIN_WITH_REGISTRATION_LOCK_ENABLED;
    }

    if (!v2Enabled && hasPin) {
      TextSecurePreferences.setV1RegistrationLockEnabled(context, false);
      return State.PIN_WITH_REGISTRATION_LOCK_DISABLED;
    }

    throw new InvalidInferredStateError(String.format(Locale.ENGLISH, "Invalid state! v1: %b, v2: %b, pin: %b", v1Enabled, v2Enabled, hasPin));
  }

  private enum State {
    /**
     * User has nothing -- either in the process of registration, or pre-PIN-migration
     */
    NO_REGISTRATION_LOCK("no_registration_lock"),

    /**
     * User has a V1 registration lock set
     */
    REGISTRATION_LOCK_V1("registration_lock_v1"),

    /**
     * User has a PIN, and registration lock is enabled.
     */
    PIN_WITH_REGISTRATION_LOCK_ENABLED("pin_with_registration_lock_enabled"),

    /**
     * User has a PIN, but registration lock is disabled.
     */
    PIN_WITH_REGISTRATION_LOCK_DISABLED("pin_with_registration_lock_disabled");

    /**
     * Using a string key so that people can rename/reorder values in the future without breaking
     * serialization.
     */
    private final String key;

    State(String key) {
      this.key = key;
    }

    public @NonNull String serialize() {
      return key;
    }

    public static @NonNull State deserialize(@NonNull String serialized) {
      for (State state : values()) {
        if (state.key.equals(serialized)) {
          return state;
        }
      }
      throw new IllegalArgumentException("No state for value: " + serialized);
    }
  }

  private static class InvalidInferredStateError extends Error {
    InvalidInferredStateError(String message) {
      super(message);
    }
  }

  private static class InvalidState_NoRegistrationLock extends IllegalStateException {}
  private static class InvalidState_RegistrationLockV1 extends IllegalStateException {}
  private static class InvalidState_PinWithRegistrationLockEnabled extends IllegalStateException {}
  private static class InvalidState_PinWithRegistrationLockDisabled extends IllegalStateException {}
}
