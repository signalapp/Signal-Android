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

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Build.VERSION;
import android.util.Log;

import org.thoughtcrime.securesms.service.SmsListener;
import org.thoughtcrime.securesms.util.SmsUtil;
import org.thoughtcrime.securesms.test.R;

public class RegistrationBypassUtil {

  private static final String TAG = RegistrationBypassUtil.class.getSimpleName();

  private static String getKeyForDevice() {
    Log.d(TAG, "release: " + VERSION.RELEASE + ", model: " + Build.MODEL);
    return VERSION.RELEASE + " - " + Build.MODEL;
  }

  public static String getPstnStringForDevice(Context context) {
    final String   DEVICE_KEY   = getKeyForDevice();
    final String[] TEST_DEVICES = context.getResources().getStringArray(R.array.test_devices);
    final String[] PSTN_STRINGS = context.getResources().getStringArray(R.array.test_pstn_numbers);
          int      deviceIndex  = -1;

    if (TEST_DEVICES.length == 0 || PSTN_STRINGS.length != TEST_DEVICES.length) {
      throw new AssertionError("one test device per pstn number required");
    }

    for (int i = 0; i < TEST_DEVICES.length; i++) {
      if (TEST_DEVICES[i].equals(DEVICE_KEY)) {
        deviceIndex = i;
        break;
      }
    }

    if (deviceIndex < 0) return PSTN_STRINGS[PSTN_STRINGS.length - 1];
    else                 return PSTN_STRINGS[deviceIndex];
  }

  public static String getVerificationCodeForPstnString(Context context, String pstnString) {
    final String[] PSTN_STRINGS = context.getResources().getStringArray(R.array.test_pstn_numbers);
    final String[] VERIFY_CODES = context.getResources().getStringArray(R.array.test_verification_codes);
          int      pstnIndex    = -1;

    if (PSTN_STRINGS.length == 0 || PSTN_STRINGS.length != VERIFY_CODES.length) {
      throw new AssertionError("one verification code per pstn number required");
    }

    for (int i = 0; i < PSTN_STRINGS.length; i++) {
      if (PSTN_STRINGS[i].equals(pstnString)) {
        pstnIndex = i;
        break;
      }
    }

    if (pstnIndex < 0) throw new AssertionError("no verification code for " + pstnString);
    else               return VERIFY_CODES[pstnIndex];
  }

  public static void receiveVerificationSms(Context context,
                                            String  pstnCountry,
                                            String  pstnNumber,
                                            String  verificationCode)
      throws Exception
  {
    final String smsVerifyMessage = "Your TextSecure verification code: " + verificationCode;
    final Intent smsVerifyIntent  = SmsUtil.buildSmsReceivedIntent(pstnCountry + pstnNumber, smsVerifyMessage);

    try {
      new SmsListener().onReceive(context, smsVerifyIntent);
    } catch (IllegalStateException e) {
      Log.w(TAG, "some api levels are picky with abortBroadcast()", e);
    }
  }

}
