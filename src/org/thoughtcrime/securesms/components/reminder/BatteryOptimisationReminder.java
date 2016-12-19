/*
 * Signal: A private messenger for Android.
 * Copyright (C) 2016  Open Whisper Systems
 *
 * This file is part of Signal.
 *
 * Signal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Signal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Signal.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.thoughtcrime.securesms.components.reminder;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.view.View.OnClickListener;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

/**
 * Posts a message in the form of a {@code Reminder} that alerts the
 * user that they are able to add Signal to the whitelist of
 * applications that can by pass Android's doze/sleep mechanism.
 *
 * @author  Alex Melbourne {@literal <alex.melbourne@protonmail.com>}
 * @version v0.1.0
 * @see     android.provider.Settings#ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
 * @since   !_TODO__ : Update this value when preparing a release.
 */
public class BatteryOptimisationReminder extends Reminder {

  public BatteryOptimisationReminder(final Context context) {
    super(context.getString(R.string.reminder_header_battery_optimisation_title),
          context.getString(R.string.reminder_header_battery_optimisation_text));

    final OnClickListener okListener = new OnClickListener() {
      @Override
      public void onClick(View v) {
        TextSecurePreferences.setPromptedBatteryOptimisation(context, true);
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        intent.setData(new Uri.Builder().scheme("package")
                                        .opaquePart(context.getPackageName())
                                        .build());
        context.startActivity(intent);
      }
    };
    final OnClickListener dismissListener = new OnClickListener() {
      @Override
      public void onClick(View v) {
        TextSecurePreferences.setPromptedBatteryOptimisation(context, true);
      }
    };
    setOkListener(okListener);
    setDismissListener(dismissListener);
  }

  public static boolean isEligible(Context context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      final PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);

      return TextSecurePreferences.isRegistered(context)                            &&
             !TextSecurePreferences.isPushRegistered(context)                       &&
             !powerManager.isIgnoringBatteryOptimizations(context.getPackageName()) &&
             !TextSecurePreferences.hasPromptedBatteryOptimisation(context);
    } else {
      return false;
    }
  }
}
