package org.thoughtcrime.securesms.util;

import android.app.Activity;
import android.content.res.Configuration;

import org.thoughtcrime.securesms.R;

public class DynamicNoActionBarTheme extends DynamicTheme {
  @Override
  protected int getSelectedTheme(Activity activity) {
    String theme = TextSecurePreferences.getTheme(activity);

    if (theme.equals(SYSTEM)) {
      int systemFlags = activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
      if(systemFlags == Configuration.UI_MODE_NIGHT_YES)
        return R.style.TextSecure_DarkNoActionBar;
      else
        return R.style.TextSecure_LightNoActionBar;
    }
    else if (theme.equals(DARK))
      return R.style.TextSecure_DarkNoActionBar;

    return R.style.TextSecure_LightNoActionBar;
  }
}
