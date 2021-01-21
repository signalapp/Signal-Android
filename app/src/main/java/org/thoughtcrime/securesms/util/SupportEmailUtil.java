package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.signal.core.util.EnglishResourceUtil;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.keyvalue.SignalStore;

import java.util.Locale;

public final class SupportEmailUtil {

  private SupportEmailUtil() { }

  public static @NonNull String getSupportEmailAddress(@NonNull Context context) {
    return context.getString(R.string.SupportEmailUtil_support_email);
  }

  /**
   * Generates a support email body with system info near the top.
   */
  public static @NonNull String generateSupportEmailBody(@NonNull Context context,
                                                         @StringRes int subject,
                                                         @Nullable String prefix,
                                                         @Nullable String suffix)
  {
    prefix = Util.firstNonNull(prefix, "");
    suffix = Util.firstNonNull(suffix, "");
    return String.format("%s\n%s\n%s", prefix, buildSystemInfo(context, subject), suffix);
  }

  private static @NonNull String buildSystemInfo(@NonNull Context context, @StringRes int subject) {
    Resources englishResources = EnglishResourceUtil.getEnglishResources(context);

    return "--- " + context.getString(R.string.HelpFragment__support_info) + " ---" +
           "\n" +
           context.getString(R.string.SupportEmailUtil_filter) + " " + englishResources.getString(subject) +
           "\n" +
           context.getString(R.string.SupportEmailUtil_device_info) + " " + getDeviceInfo() +
           "\n" +
           context.getString(R.string.SupportEmailUtil_android_version) + " " + getAndroidVersion() +
           "\n" +
           context.getString(R.string.SupportEmailUtil_signal_version) + " " + getSignalVersion() +
           "\n" +
           context.getString(R.string.SupportEmailUtil_signal_package) + " " + getSignalPackage(context) +
           "\n" +
           context.getString(R.string.SupportEmailUtil_registration_lock) + " " + getRegistrationLockEnabled(context) +
           "\n" +
           context.getString(R.string.SupportEmailUtil_locale) + " " + Locale.getDefault().toString();
  }

  private static CharSequence getDeviceInfo() {
    return String.format("%s %s (%s)", Build.MANUFACTURER, Build.MODEL, Build.PRODUCT);
  }

  private static CharSequence getAndroidVersion() {
    return String.format("%s (%s, %s)", Build.VERSION.RELEASE, Build.VERSION.INCREMENTAL, Build.DISPLAY);
  }

  private static CharSequence getSignalVersion() {
    return BuildConfig.VERSION_NAME;
  }

  private static CharSequence getSignalPackage(@NonNull Context context) {
    return String.format("%s (%s)", BuildConfig.APPLICATION_ID, AppSignatureUtil.getAppSignature(context).or("Unknown"));
  }

  private static CharSequence getRegistrationLockEnabled(@NonNull Context context) {
    return String.valueOf(TextSecurePreferences.isV1RegistrationLockEnabled(context) || SignalStore.kbsValues().isV2RegistrationLockEnabled());
  }
}
