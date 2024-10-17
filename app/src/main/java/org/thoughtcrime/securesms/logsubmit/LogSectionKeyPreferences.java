package org.thoughtcrime.securesms.logsubmit;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.keyvalue.KeepMessagesDuration;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;

final class LogSectionKeyPreferences implements LogSection {

  @Override
  public @NonNull String getTitle() {
    return "KEY PREFERENCES";
  }

  @Override
  public @NonNull CharSequence getContent(@NonNull Context context) {
    return new StringBuilder().append("Screen Lock              : ").append(SignalStore.settings().getScreenLockEnabled()).append("\n")
                              .append("Screen Lock Timeout      : ").append(SignalStore.settings().getScreenLockTimeout()).append("\n")
                              .append("Password Disabled        : ").append(SignalStore.settings().getPassphraseDisabled()).append("\n")
                              .append("Prefer Contact Photos    : ").append(SignalStore.settings().isPreferSystemContactPhotos()).append("\n")
                              .append("Call Data Mode           : ").append(SignalStore.settings().getCallDataMode()).append("\n")
                              .append("Media Quality            : ").append(SignalStore.settings().getSentMediaQuality()).append("\n")
                              .append("Client Deprecated        : ").append(SignalStore.misc().isClientDeprecated()).append("\n")
                              .append("Push Registered          : ").append(SignalStore.account().isRegistered()).append("\n")
                              .append("Unauthorized Received    : ").append(TextSecurePreferences.isUnauthorizedReceived(context)).append("\n")
                              .append("self.isRegistered()      : ").append(SignalStore.account().getAci() == null ? "false"     : Recipient.self().isRegistered()).append("\n")
                              .append("Thread Trimming          : ").append(getThreadTrimmingString()).append("\n")
                              .append("Censorship Setting       : ").append(SignalStore.settings().getCensorshipCircumventionEnabled()).append("\n")
                              .append("Network Reachable        : ").append(SignalStore.misc().isServiceReachableWithoutCircumvention()).append(", last checked: ").append(SignalStore.misc().getLastCensorshipServiceReachabilityCheckTime()).append("\n")
                              .append("Wifi Download            : ").append(Util.join(TextSecurePreferences.getWifiMediaDownloadAllowed(context), ",")).append("\n")
                              .append("Roaming Download         : ").append(Util.join(TextSecurePreferences.getRoamingMediaDownloadAllowed(context), ",")).append("\n")
                              .append("Mobile Download          : ").append(Util.join(TextSecurePreferences.getMobileMediaDownloadAllowed(context), ",")).append("\n")
                              .append("Phone Number Sharing     : ").append(SignalStore.phoneNumberPrivacy().isPhoneNumberSharingEnabled()).append(" (").append(SignalStore.phoneNumberPrivacy().getPhoneNumberSharingMode()).append(")\n")
                              .append("Phone Number Discoverable: ").append(SignalStore.phoneNumberPrivacy().getPhoneNumberDiscoverabilityMode()).append("\n")
                              .append("Incognito keyboard       : ").append(TextSecurePreferences.isIncognitoKeyboardEnabled(context)).append("\n");
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
