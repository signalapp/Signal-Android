/**
 * Copyright (C) 2013-2014 Open WhisperSystems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.util;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.preference.PreferenceManager;

import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.ConversationActivity;
import org.thoughtcrime.securesms.ConversationListActivity;
import org.thoughtcrime.securesms.R;

public class DynamicTheme {

  private int currentTheme;

  public void onCreate(Activity activity) {
    currentTheme = getSelectedTheme(activity);
    activity.setTheme(currentTheme);
  }

  public void onResume(Activity activity) {
    if (currentTheme != getSelectedTheme(activity)) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
        activity.recreate();
      } else {
        Intent intent = activity.getIntent();
        activity.finish();
        OverridePendingTransition.invoke(activity);
        activity.startActivity(intent);
        OverridePendingTransition.invoke(activity);
      }
    }
  }

  private static int getSelectedTheme(Activity activity) {
    String theme = TextSecurePreferences.getTheme(activity);

    if (theme.equals("light")) {
      if (activity instanceof ConversationListActivity) return R.style.TextSecure_LightTheme_NavigationDrawer;
      else if (activity instanceof ConversationActivity) return R.style.TextSecure_LightTheme_ConversationActivity;
      else                                              return R.style.TextSecure_LightTheme;
    } else if (theme.equals("dark")) {
      if (activity instanceof ConversationListActivity) return R.style.TextSecure_DarkTheme_NavigationDrawer;
      else if (activity instanceof ConversationActivity) return R.style.TextSecure_DarkTheme_ConversationActivity;
      else                                              return R.style.TextSecure_DarkTheme;
    }

    return R.style.TextSecure_LightTheme;
  }

  private static final class OverridePendingTransition {
    static void invoke(Activity activity) {
      activity.overridePendingTransition(0, 0);
    }
  }
}
