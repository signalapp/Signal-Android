package org.thoughtcrime.securesms.util;

import android.app.Activity;

import org.thoughtcrime.securesms.R;

public class DynamicNoActionBarTheme extends DynamicTheme {
  @Override
  protected int getSelectedTheme(Activity activity) {
    return R.style.TextSecure_DarkNoActionBar;
  }
}
