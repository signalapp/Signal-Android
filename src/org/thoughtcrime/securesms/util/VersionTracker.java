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

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;

public class VersionTracker {

  private static final String LAST_VERSION_CODE = "last_version_code";

  public static int getLastSeenVersion(Context context) {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
    return preferences.getInt(LAST_VERSION_CODE, 0);
  }

  public static void updateLastSeenVersion(Context context) {
    try {
      SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
      int currentVersionCode        = context.getPackageManager()
                                             .getPackageInfo(context.getPackageName(), 0)
                                             .versionCode;
      preferences.edit().putInt(LAST_VERSION_CODE, currentVersionCode).commit();
    } catch (PackageManager.NameNotFoundException e) {
      throw new AssertionError(e);
    }
  }
}
