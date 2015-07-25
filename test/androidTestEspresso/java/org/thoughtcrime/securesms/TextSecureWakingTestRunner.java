/**
 * Copyright (C) 2015 Open Whisper Systems
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
package org.thoughtcrime.securesms;

import android.app.Application;
import android.app.KeyguardManager;
import android.content.Context;
import android.os.PowerManager;
import android.support.test.runner.AndroidJUnitRunner;
import android.util.Log;

public class TextSecureWakingTestRunner extends AndroidJUnitRunner {

  @Override public void onStart() {
    runOnMainSync(new Runnable() {
      @Override public void run() {
        Application app        = (Application) getTargetContext().getApplicationContext();
        String      simpleName = TextSecureWakingTestRunner.class.getSimpleName();

        ((KeyguardManager) app.getSystemService(Context.KEYGUARD_SERVICE))
            .newKeyguardLock(simpleName)
            .disableKeyguard();

        ((PowerManager) app.getSystemService(Context.POWER_SERVICE))
            .newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, simpleName)
            .acquire();
      }
    });
    super.onStart();
  }

}
