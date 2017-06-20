/*
 * Copyright (C) 2017 Fernando Garcia Alvarez
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thoughtcrime.securesms.util;

import android.app.Activity;
import android.content.Intent;

import org.thoughtcrime.securesms.R;

public class DynamicTheme {
  public static final String AUTO_DARK = "auto_dark";
  public static final String DARK  = "dark";
  public static final String LIGHT = "light";

  private int currentTheme;

  public void onCreate(Activity activity) {
    currentTheme = getSelectedTheme(activity);
    activity.setTheme(currentTheme);
  }

  public void onResume(Activity activity) {
    if (currentTheme != getSelectedTheme(activity)) {
      Intent intent = activity.getIntent();
      activity.finish();
      OverridePendingTransition.invoke(activity);
      activity.startActivity(intent);
      OverridePendingTransition.invoke(activity);
    }
  }

  protected int getSelectedTheme(Activity activity) {
    String theme = TextSecurePreferences.getTheme(activity);

    if (theme.equals(DARK)) return R.style.TextSecure_DarkTheme;

    if (theme.equals(AUTO_DARK))
      return TimeHelper.isNight(activity) ? R.style.TextSecure_DarkTheme : R.style.TextSecure_LightTheme;

    return R.style.TextSecure_LightTheme;
  }

  private static final class OverridePendingTransition {
    static void invoke(Activity activity) {
      activity.overridePendingTransition(0, 0);
    }
  }
}
