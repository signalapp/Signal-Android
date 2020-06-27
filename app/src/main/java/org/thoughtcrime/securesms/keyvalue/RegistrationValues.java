package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;

public final class RegistrationValues extends SignalStoreValues {

  private static final String REGISTRATION_COMPLETE = "registration.complete";
  private static final String PIN_REQUIRED          = "registration.pin_required";

  RegistrationValues(@NonNull KeyValueStore store) {
    super(store);
  }

  public synchronized void onFirstEverAppLaunch() {
    getStore().beginWrite()
              .putBoolean(REGISTRATION_COMPLETE, false)
              .putBoolean(PIN_REQUIRED, true)
              .commit();
  }

  public synchronized void clearRegistrationComplete() {
    onFirstEverAppLaunch();
  }

  public synchronized void setRegistrationComplete() {
    getStore().beginWrite()
              .putBoolean(REGISTRATION_COMPLETE, true)
              .commit();
  }

  @CheckResult
  public synchronized boolean pinWasRequiredAtRegistration() {
    return getStore().getBoolean(PIN_REQUIRED, false);
  }

  @CheckResult
  public synchronized boolean isRegistrationComplete() {
    return getStore().getBoolean(REGISTRATION_COMPLETE, true);
  }
}
