package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.signal.core.util.ResourceUtil;
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
                                                         @StringRes int filter,
                                                         @Nullable String prefix,
                                                         @Nullable String suffix)
  {
    return generateSupportEmailBody(context, filter, null, prefix, suffix);
  }

  /**
   * Generates a support email body with system info near the top.
   */
  public static @NonNull String generateSupportEmailBody(@NonNull Context context,
                                                         @StringRes int filter,
                                                         @Nullable String filterSuffix,
                                                         @Nullable String prefix,
                                                         @Nullable String suffix)
  {
    filterSuffix = Util.emptyIfNull(filterSuffix);
    prefix       = Util.emptyIfNull(prefix);
    suffix       = Util.emptyIfNull(suffix);

    return String.format("%s\n%s\n%s", prefix, buildSystemInfo(context, filter, filterSuffix), suffix);
  }

  private static @NonNull String buildSystemInfo(@NonNull Context context, @StringRes int filter, @NonNull String filterSuffix) {
    Resources englishResources = ResourceUtil.getEnglishResources(context);

    return "--- " + context.getString(R.string.HelpFragment__support_info) + " ---" +
           "\n" +
           context.getString(R.string.SupportEmailUtil_filter) + " " + englishResources.getString(filter) + filterSuffix +
           "\n" +
           context.getString(R.string.SupportEmailUtil_device_info) + " " + getDeviceInfo() +
           "\n" +
           context.getString(R.string.SupportEmailUtil_android_version) + " " + getAndroidVersion() +
           "\n" +
           context.getString(R.string.SupportEmailUtil_signal_version) + " " + getSignalVersion() +
           "\n" +
           context.getString(R.string.SupportEmailUtil_signal_package) + " " + getSignalPackage(context) +
           "\n" +
           context.getString(R.string.SupportEmailUtil_registration_lock) + " " + getRegistrationLockEnabled() +
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
    return String.format("%s (%s)", BuildConfig.APPLICATION_ID, AppSignatureUtil.getAppSignature(context));
  }

  private static CharSequence getRegistrationLockEnabled() {
    return String.valueOf(SignalStore.svr().isRegistrationLockEnabled());
  }
}
