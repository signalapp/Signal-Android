/**
 * Copyright (C) 2016 Open Whisper Systems
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

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import org.iilab.IilabEngineeringRSA2048Pin;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import info.guardianproject.GuardianProjectRSA4096;
import info.guardianproject.trustedintents.TrustedIntents;

/**
 * Respond to a PanicKit trigger Intent by locking the app.  PanicKit provides a
 * common framework for creating "panic button" apps that can trigger actions
 * in "panic responder" apps.  In this case, the response is to lock the app,
 * if it has been configured to do so.
 * <p/>
 * This uses the TrustedIntents library to make sure that the apps sending the
 * panic trigger Intents come from APKs that have been signed by the official
 * signing key of Amnesty/iilab Panic Button and Guardian Project Ripple.
 * <p/>
 * {@link GuardianProjectRSA4096} is the signing key used for Guardian Project
 * Ripple (info.guardianproject.ripple).  {@link IilabEngineeringRSA2048Pin} is
 * the signing key use for Amnesty International's Panic Button, made by
 * iilab.org (org.iilab.pb)
 */
public class PanicResponderActivity extends Activity {

  private static final String TAG = PanicResponderActivity.class.getSimpleName();

  public static final String PANIC_TRIGGER_ACTION = "info.guardianproject.panic.action.TRIGGER";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    TrustedIntents trustedIntents = TrustedIntents.get(this);
    trustedIntents.addTrustedSigner(GuardianProjectRSA4096.class);
    trustedIntents.addTrustedSigner(IilabEngineeringRSA2048Pin.class);

    Intent intent = trustedIntents.getIntentFromTrustedSender(this);
    if (intent != null
            && !TextSecurePreferences.isPasswordDisabled(this)
            && PANIC_TRIGGER_ACTION.equals(intent.getAction())) {
      handleClearPassphrase();
    }

    if (Build.VERSION.SDK_INT >= 21) {
      finishAndRemoveTask();
    } else {
      finish();
    }
  }

  private void handleClearPassphrase() {
    Intent intent = new Intent(this, KeyCachingService.class);
    intent.setAction(KeyCachingService.CLEAR_KEY_ACTION);
    startService(intent);
  }
}
