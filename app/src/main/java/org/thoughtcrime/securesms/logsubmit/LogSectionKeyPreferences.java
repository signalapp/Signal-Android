package org.thoughtcrime.securesms.logsubmit;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.keyvalue.KeepMessagesDuration;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

final class LogSectionKeyPreferences implements LogSection {

  @Override
  public @NonNull String getTitle() {
    return "KEY PREFERENCES";
  }

  @Override
  public @NonNull CharSequence getContent(@NonNull Context context) {
    return new StringBuilder().append("Screen Lock          : ").append(TextSecurePreferences.isScreenLockEnabled(context)).append("\n")
                              .append("Screen Lock Timeout  : ").append(TextSecurePreferences.getScreenLockTimeout(context)).append("\n")
                              .append("Password Disabled    : ").append(TextSecurePreferences.isPasswordDisabled(context)).append("\n")
                              .append("Prefer Contact Photos: ").append(SignalStore.settings().isPreferSystemContactPhotos()).append("\n")
                              .append("Call Bandwidth Mode  : ").append(SignalStore.settings().getCallBandwidthMode()).append("\n")
                              .append("Media Quality        : ").append(SignalStore.settings().getSentMediaQuality()).append("\n")
                              .append("Client Deprecated    : ").append(SignalStore.misc().isClientDeprecated()).append("\n")
                              .append("Push Registered      : ").append(SignalStore.account().isRegistered()).append("\n")
                              .append("Unauthorized Received: ").append(TextSecurePreferences.isUnauthorizedReceived(context)).append("\n")
                              .append("self.isRegistered()  : ").append(SignalStore.account().getAci() == null ? "false" : Recipient.self().isRegistered()).append("\n")
                              .append("Thread Trimming      : ").append(getThreadTrimmingString()).append("\n")
                              .append("Censorship Setting   : ").append(SignalStore.settings().getCensorshipCircumventionEnabled()).append("\n")
                              .append("Network Reachable    : ").append(SignalStore.misc().isServiceReachableWithoutCircumvention()).append(", last checked: ").append(SignalStore.misc().getLastCensorshipServiceReachabilityCheckTime()).append("\n");
  }

  private static String getThreadTrimmingString() {
    if (SignalStore.settings().isTrimByLengthEnabled()) {
      return "Enabled - Max length of " + SignalStore.settings().getThreadTrimLength();
    } else if (SignalStore.settings().getKeepMessagesDuration() != KeepMessagesDuration.FOREVER) {
      return "Enabled - Max age of " + SignalStore.settings().getKeepMessagesDuration();
    } else {
      return "Disabled";
    }
  }
}
