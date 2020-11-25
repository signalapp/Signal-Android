package org.thoughtcrime.securesms.util;

import android.app.Activity;

import network.loki.messenger.R;

public class DynamicNoActionBarTheme extends DynamicTheme {
  @Override
  protected int getSelectedTheme(Activity activity) {
    return R.style.Theme_TextSecure_DayNight_NoActionBar;
  }
}
