package org.thoughtcrime.securesms.logsubmit;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.keyvalue.SignalStore;

public class LogSectionPin implements LogSection {

  @Override
  public @NonNull String getTitle() {
    return "PIN STATE";
  }

  @Override
  public @NonNull CharSequence getContent(@NonNull Context context) {
    return new StringBuilder().append("Last Successful Reminder Entry: ").append(SignalStore.pinValues().getLastSuccessfulEntryTime()).append("\n")
                              .append("Next Reminder Interval: ").append(SignalStore.pinValues().getCurrentInterval()).append("\n")
                              .append("Reglock: ").append(SignalStore.svr().isRegistrationLockEnabled()).append("\n")
                              .append("Signal PIN: ").append(SignalStore.svr().hasPin()).append("\n")
                              .append("Opted Out: ").append(SignalStore.svr().hasOptedOut()).append("\n")
                              .append("Last Creation Failed: ").append(SignalStore.svr().lastPinCreateFailed()).append("\n")
                              .append("Needs Account Restore: ").append(SignalStore.storageService().needsAccountRestore()).append("\n")
                              .append("PIN Required at Registration: ").append(SignalStore.registrationValues().pinWasRequiredAtRegistration()).append("\n")
                              .append("Registration Complete: ").append(SignalStore.registrationValues().isRegistrationComplete());

  }
}
