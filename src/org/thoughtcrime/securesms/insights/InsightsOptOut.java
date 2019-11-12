package org.thoughtcrime.securesms.insights;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.util.TextSecurePreferences;

class InsightsOptOut {
  private static final String INSIGHTS_OPT_OUT_PREFERENCE = "insights.opt.out";

  static boolean userHasOptedOut(@NonNull Context context) {
    return TextSecurePreferences.getBooleanPreference(context, INSIGHTS_OPT_OUT_PREFERENCE, false);
  }

  static void userRequestedOptOut(@NonNull Context context) {
    TextSecurePreferences.setBooleanPreference(context, INSIGHTS_OPT_OUT_PREFERENCE, true);
  }
}
