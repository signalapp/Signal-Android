package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;

public final class RegistrationValues {

  private static final String REGISTRATION_COMPLETE = "registration.complete";
  private static final String PIN_REQUIRED          = "registration.pin_required";

  private final KeyValueStore store;

  RegistrationValues(@NonNull KeyValueStore store) {
    this.store = store;
  }

  public synchronized void onFirstEverAppLaunch() {
    store.beginWrite()
         .putBoolean(REGISTRATION_COMPLETE, false)
         .putBoolean(PIN_REQUIRED, true)
         .commit();
  }

  public synchronized void clearRegistrationComplete() {
    onFirstEverAppLaunch();
  }

  public synchronized void setRegistrationComplete() {
    store.beginWrite()
         .putBoolean(REGISTRATION_COMPLETE, true)
         .commit();
  }

  @CheckResult
  public synchronized boolean pinWasRequiredAtRegistration() {
    return store.getBoolean(PIN_REQUIRED, false);
  }

  @CheckResult
  public synchronized boolean isRegistrationComplete() {
    return store.getBoolean(REGISTRATION_COMPLETE, true);
  }
}
