package org.thoughtcrime.securesms.insights;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.util.TextSecurePreferences;

public final class InsightsOptOut {
  private static final String INSIGHTS_OPT_OUT_PREFERENCE = "insights.opt.out";

  private InsightsOptOut() {
  }

  static boolean userHasOptedOut(@NonNull Context context) {
    return TextSecurePreferences.getBooleanPreference(context, INSIGHTS_OPT_OUT_PREFERENCE, false);
  }

  public static void userRequestedOptOut(@NonNull Context context) {
    TextSecurePreferences.setBooleanPreference(context, INSIGHTS_OPT_OUT_PREFERENCE, true);
  }
}
