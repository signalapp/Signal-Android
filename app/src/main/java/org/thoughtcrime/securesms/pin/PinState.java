package org.thoughtcrime.securesms.pin;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.thoughtcrime.securesms.KbsEnclave;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.JobTracker;
import org.thoughtcrime.securesms.jobs.ClearFallbackKbsEnclaveJob;
import org.thoughtcrime.securesms.jobs.RefreshAttributesJob;
import org.thoughtcrime.securesms.jobs.StorageForcePushJob;
import org.thoughtcrime.securesms.keyvalue.KbsValues;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.lock.PinHashing;
import org.thoughtcrime.securesms.lock.RegistrationLockReminders;
import org.thoughtcrime.securesms.lock.v2.PinKeyboardType;
import org.thoughtcrime.securesms.megaphone.Megaphones;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.KbsPinData;
import org.whispersystems.signalservice.api.KeyBackupService;
import org.whispersystems.signalservice.api.kbs.HashedPin;
import org.whispersystems.signalservice.api.kbs.MasterKey;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedResponseException;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class PinState {

  private static final String TAG = Log.tag(PinState.class);

  /**
   * Invoked after a user has successfully registered. Ensures all the necessary state is updated.
   */
  public static synchronized void onRegistration(@NonNull Context context,
                                                 @Nullable KbsPinData kbsData,
                                                 @Nullable String pin,
                                                 boolean hasPinToRestore,
                                                 boolean setRegistrationLockEnabled)
  {
    Log.i(TAG, "onRegistration()");

    TextSecurePreferences.setV1RegistrationLockPin(context, pin);

    if (kbsData == null && pin != null) {
      Log.i(TAG, "Registration Lock V1");
      SignalStore.kbsValues().clearRegistrationLockAndPin();
      TextSecurePreferences.setV1RegistrationLockEnabled(context, true);
      TextSecurePreferences.setRegistrationLockLastReminderTime(context, System.currentTimeMillis());
      TextSecurePreferences.setRegistrationLockNextReminderInterval(context, RegistrationLockReminders.INITIAL_INTERVAL);
    } else if (kbsData != null && pin != null) {
      if (setRegistrationLockEnabled) {
        Log.i(TAG, "Registration Lock V2");
        TextSecurePreferences.setV1RegistrationLockEnabled(context, false);
        SignalStore.kbsValues().setV2RegistrationLockEnabled(true);
      } else {
        Log.i(TAG, "ReRegistration Skip SMS");
      }
      SignalStore.kbsValues().setKbsMasterKey(kbsData, pin);
      SignalStore.pinValues().resetPinReminders();
      resetPinRetryCount(context, pin);
      ClearFallbackKbsEnclaveJob.clearAll();
    } else if (hasPinToRestore) {
      Log.i(TAG, "Has a PIN to restore.");
      SignalStore.kbsValues().clearRegistrationLockAndPin();
      TextSecurePreferences.setV1RegistrationLockEnabled(context, false);
      SignalStore.storageService().setNeedsAccountRestore(true);
    } else {
      Log.i(TAG, "No registration lock or PIN at all.");
      SignalStore.kbsValues().clearRegistrationLockAndPin();
      TextSecurePreferences.setV1RegistrationLockEnabled(context, false);
    }
  }

  /**
   * Invoked when the user is going through the PIN restoration flow (which is separate from reglock).
   */
  public static synchronized void onSignalPinRestore(@NonNull Context context, @NonNull KbsPinData kbsData, @NonNull String pin) {
    Log.i(TAG, "onSignalPinRestore()");

    SignalStore.kbsValues().setKbsMasterKey(kbsData, pin);
    SignalStore.kbsValues().setV2RegistrationLockEnabled(false);
    SignalStore.pinValues().resetPinReminders();
    SignalStore.kbsValues().setPinForgottenOrSkipped(false);
    SignalStore.storageService().setNeedsAccountRestore(false);
    resetPinRetryCount(context, pin);
    ClearFallbackKbsEnclaveJob.clearAll();
  }

  /**
   * Invoked when the user skips out on PIN restoration or otherwise fails to remember their PIN.
   */
  public static synchronized void onPinRestoreForgottenOrSkipped() {
    SignalStore.kbsValues().clearRegistrationLockAndPin();
    SignalStore.storageService().setNeedsAccountRestore(false);
    SignalStore.kbsValues().setPinForgottenOrSkipped(true);
  }

  /**
   * Invoked whenever the Signal PIN is changed or created.
   */
  @WorkerThread
  public static synchronized void onPinChangedOrCreated(@NonNull Context context, @NonNull String pin, @NonNull PinKeyboardType keyboard)
      throws IOException, UnauthenticatedResponseException, InvalidKeyException
  {
    Log.i(TAG, "onPinChangedOrCreated()");

    KbsEnclave                        kbsEnclave       = KbsEnclaves.current();
    KbsValues                         kbsValues        = SignalStore.kbsValues();
    boolean                           isFirstPin       = !kbsValues.hasPin() || kbsValues.hasOptedOut();
    MasterKey                         masterKey        = kbsValues.getOrCreateMasterKey();
    KeyBackupService                  keyBackupService = ApplicationDependencies.getKeyBackupService(kbsEnclave);
    KeyBackupService.PinChangeSession pinChangeSession = keyBackupService.newPinChangeSession();
    HashedPin                         hashedPin        = PinHashing.hashPin(pin, pinChangeSession);
    KbsPinData                        kbsData          = pinChangeSession.setPin(hashedPin, masterKey);

    kbsValues.setKbsMasterKey(kbsData, pin);
    kbsValues.setPinForgottenOrSkipped(false);
    TextSecurePreferences.clearRegistrationLockV1(context);
    SignalStore.pinValues().setKeyboardType(keyboard);
    SignalStore.pinValues().resetPinReminders();
    ApplicationDependencies.getMegaphoneRepository().markFinished(Megaphones.Event.PINS_FOR_ALL);

    if (isFirstPin) {
      Log.i(TAG, "First time setting a PIN. Refreshing attributes to set the 'storage' capability. Enclave: " + kbsEnclave.getEnclaveName());
      bestEffortRefreshAttributes();
    } else {
      Log.i(TAG, "Not the first time setting a PIN. Enclave: " + kbsEnclave.getEnclaveName());
      ApplicationDependencies.getJobManager().add(new RefreshAttributesJob());
    }
  }

  /**
   * Invoked when PIN creation fails.
   */
  public static synchronized void onPinCreateFailure() {
    Log.i(TAG, "onPinCreateFailure()");
    if (getState() == State.NO_REGISTRATION_LOCK) {
      SignalStore.kbsValues().onPinCreateFailure();
    }
  }

  /**
   * Invoked when the user has enabled the "PIN opt out" setting.
   */
  @WorkerThread
  public static synchronized void onPinOptOut() {
    Log.i(TAG, "onPinOptOutEnabled()");
    assertState(State.PIN_WITH_REGISTRATION_LOCK_DISABLED, State.NO_REGISTRATION_LOCK);

    optOutOfPin();
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


    KbsEnclave kbsEnclave = KbsEnclaves.current();
    Log.i(TAG, "Enclave: " + kbsEnclave.getEnclaveName());

    SignalStore.kbsValues().setV2RegistrationLockEnabled(false);
    ApplicationDependencies.getKeyBackupService(kbsEnclave)
                           .newPinChangeSession(SignalStore.kbsValues().getRegistrationLockTokenResponse())
                           .enableRegistrationLock(SignalStore.kbsValues().getOrCreateMasterKey());
    SignalStore.kbsValues().setV2RegistrationLockEnabled(true);
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
    ApplicationDependencies.getKeyBackupService(KbsEnclaves.current())
                           .newPinChangeSession(SignalStore.kbsValues().getRegistrationLockTokenResponse())
                           .disableRegistrationLock();
    SignalStore.kbsValues().setV2RegistrationLockEnabled(false);
  }

  /**
   * Should only be called by {@link org.thoughtcrime.securesms.migrations.RegistrationPinV2MigrationJob}.
   */
  @WorkerThread
  public static synchronized void onMigrateToRegistrationLockV2(@NonNull Context context, @NonNull String pin)
      throws IOException, UnauthenticatedResponseException, InvalidKeyException
  {
    Log.i(TAG, "onMigrateToRegistrationLockV2()");

    KbsEnclave kbsEnclave = KbsEnclaves.current();
    Log.i(TAG, "Enclave: " + kbsEnclave.getEnclaveName());

    KbsValues                         kbsValues        = SignalStore.kbsValues();
    MasterKey                         masterKey        = kbsValues.getOrCreateMasterKey();
    KeyBackupService                  keyBackupService = ApplicationDependencies.getKeyBackupService(kbsEnclave);
    KeyBackupService.PinChangeSession pinChangeSession = keyBackupService.newPinChangeSession();
    HashedPin                         hashedPin        = PinHashing.hashPin(pin, pinChangeSession);
    KbsPinData                        kbsData          = pinChangeSession.setPin(hashedPin, masterKey);

    pinChangeSession.enableRegistrationLock(masterKey);

    kbsValues.setKbsMasterKey(kbsData, pin);
    TextSecurePreferences.clearRegistrationLockV1(context);
  }

  /**
   * Should only be called by {@link org.thoughtcrime.securesms.jobs.KbsEnclaveMigrationWorkerJob}.
   */
  @WorkerThread
  public static synchronized void onMigrateToNewEnclave(@NonNull String pin)
      throws IOException, UnauthenticatedResponseException
  {
    Log.i(TAG, "onMigrateToNewEnclave()");
    assertState(State.PIN_WITH_REGISTRATION_LOCK_DISABLED, State.PIN_WITH_REGISTRATION_LOCK_ENABLED);

    Log.i(TAG, "Migrating to enclave " + KbsEnclaves.current().getEnclaveName());
    setPinOnEnclave(KbsEnclaves.current(), pin, SignalStore.kbsValues().getOrCreateMasterKey());

    ClearFallbackKbsEnclaveJob.clearAll();
  }

  @WorkerThread
  private static void bestEffortRefreshAttributes() {
    Optional<JobTracker.JobState> result = ApplicationDependencies.getJobManager().runSynchronously(new RefreshAttributesJob(), TimeUnit.SECONDS.toMillis(10));

    if (result.isPresent() && result.get() == JobTracker.JobState.SUCCESS) {
      Log.i(TAG, "Attributes were refreshed successfully.");
    } else if (result.isPresent()) {
      Log.w(TAG, "Attribute refresh finished, but was not successful. Enqueuing one for later. (Result: " + result.get() + ")");
      ApplicationDependencies.getJobManager().add(new RefreshAttributesJob());
    } else {
      Log.w(TAG, "Job did not finish in the allotted time. It'll finish later.");
    }
  }

  @WorkerThread
  private static void bestEffortForcePushStorage() {
    Optional<JobTracker.JobState> result = ApplicationDependencies.getJobManager().runSynchronously(new StorageForcePushJob(), TimeUnit.SECONDS.toMillis(10));

    if (result.isPresent() && result.get() == JobTracker.JobState.SUCCESS) {
      Log.i(TAG, "Storage was force-pushed successfully.");
    } else if (result.isPresent()) {
      Log.w(TAG, "Storage force-pushed finished, but was not successful. Enqueuing one for later. (Result: " + result.get() + ")");
      ApplicationDependencies.getJobManager().add(new RefreshAttributesJob());
    } else {
      Log.w(TAG, "Storage fore push did not finish in the allotted time. It'll finish later.");
    }
  }

  @WorkerThread
  private static void resetPinRetryCount(@NonNull Context context, @Nullable String pin) {
    if (pin == null) {
      return;
    }

    try {
      setPinOnEnclave(KbsEnclaves.current(), pin, SignalStore.kbsValues().getOrCreateMasterKey());
      TextSecurePreferences.clearRegistrationLockV1(context);
      Log.i(TAG, "Pin set/attempts reset on KBS");
    } catch (IOException e) {
      Log.w(TAG, "May have failed to reset pin attempts!", e);
    } catch (UnauthenticatedResponseException e) {
      Log.w(TAG, "Failed to reset pin attempts", e);
    }
  }

  @WorkerThread
  private static @NonNull KbsPinData setPinOnEnclave(@NonNull KbsEnclave enclave, @NonNull String pin, @NonNull MasterKey masterKey)
      throws IOException, UnauthenticatedResponseException
  {
    Log.i(TAG, "Setting PIN on enclave: " + enclave.getEnclaveName());

    KeyBackupService                  kbs              = ApplicationDependencies.getKeyBackupService(enclave);
    KeyBackupService.PinChangeSession pinChangeSession = kbs.newPinChangeSession();
    HashedPin                         hashedPin        = PinHashing.hashPin(pin, pinChangeSession);
    KbsPinData                        newData          = pinChangeSession.setPin(hashedPin, masterKey);

    SignalStore.kbsValues().setKbsMasterKey(newData, pin);

    return newData;
  }

  @WorkerThread
  private static void optOutOfPin() {
    SignalStore.kbsValues().optOut();

    ApplicationDependencies.getMegaphoneRepository().markFinished(Megaphones.Event.PINS_FOR_ALL);

    bestEffortRefreshAttributes();
    bestEffortForcePushStorage();
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
      case PIN_OPT_OUT:                         throw new InvalidState_PinOptOut();
      default:                                  throw new IllegalStateException("Expected: " + Arrays.toString(allowed) + ", Actual: " + currentState);
    }
  }

  public static @NonNull State getState() {
    Context   context   = ApplicationDependencies.getApplication();
    KbsValues kbsValues = SignalStore.kbsValues();

    boolean v1Enabled = TextSecurePreferences.isV1RegistrationLockEnabled(context);
    boolean v2Enabled = kbsValues.isV2RegistrationLockEnabled();
    boolean hasPin    = kbsValues.hasPin();
    boolean optedOut  = kbsValues.hasOptedOut();

    if (optedOut && !v2Enabled && !v1Enabled) {
      return State.PIN_OPT_OUT;
    }

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
    PIN_WITH_REGISTRATION_LOCK_DISABLED("pin_with_registration_lock_disabled"),

    /**
     * The user has opted out of creating a PIN. In this case, we will generate a high-entropy PIN
     * on their behalf.
     */
    PIN_OPT_OUT("pin_opt_out");

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
  private static class InvalidState_PinOptOut extends IllegalStateException {}
}
