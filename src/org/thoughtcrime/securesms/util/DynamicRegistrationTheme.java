package org.thoughtcrime.securesms.util;

import android.app.Activity;

import network.loki.messenger.R;

public class DynamicRegistrationTheme extends DynamicTheme {
  @Override
  protected int getSelectedTheme(Activity activity) {
    return R.style.TextSecure_DarkRegistrationTheme;
  }
}
