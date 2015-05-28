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

import android.app.Activity;
import android.content.Context;
import android.preference.PreferenceManager;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

public class TextSecureEspressoTestCase<T extends Activity> extends ActivityInstrumentationTestCase2<T> {

  private static final String TAG = TextSecureEspressoTestCase.class.getSimpleName();

  public static final int STATE_BASE                 = 0x00000000;
  public static final int STATE_REGISTRATION_SKIPPED = 0x00000001;
  public static final int STATE_REGISTERED           = 0x00000002;

  private static final long REGISTRATION_RATE_LIMIT_MS = 60000 + 10000;
  private static       long TIME_LAST_REGISTERED       = 0L;

  protected String pstnCountry;
  protected String pstnNumber;
  protected String verificationCode;

  public TextSecureEspressoTestCase(Class<T> clazz) {
    super(clazz);
  }

  protected static void sleepThroughRegistrationLimit() {
    long msPassedSinceReg = System.currentTimeMillis() - TIME_LAST_REGISTERED;
    long msSleepRemaining = REGISTRATION_RATE_LIMIT_MS - msPassedSinceReg;

    Log.d(TAG, "sleeping for " + msSleepRemaining + "ms to avoid registration rate limit");
    while (msSleepRemaining > 0) {
      try {

        Thread.sleep(1000);
        msSleepRemaining -= 1000;

      } catch (InterruptedException e) { }
    }
    TIME_LAST_REGISTERED = System.currentTimeMillis();
  }

  protected Context getContext() {
    return getInstrumentation().getTargetContext();
  }

  private void initBaseState() throws Exception {
    EspressoUtil.removeAllContacts(getContext());
    DatabaseFactory.getDraftDatabase(getContext()).clearAllDrafts();
    DatabaseFactory.getThreadDatabase(getContext()).deleteAllConversations();
    PreferenceManager.getDefaultSharedPreferences(getContext()).edit().clear().commit();
    getContext().getSharedPreferences(MasterSecretUtil.PREFERENCES_NAME, 0).edit().clear().commit();
    getContext().getSharedPreferences("SecureSMS", 0).edit().clear().commit();
  }

  protected void loadActivity(Class<? extends Activity> clazz, int state) throws Exception {
    switch (state) {
      case STATE_REGISTRATION_SKIPPED:
        TextSecurePreferences.setPromptedPushRegistration(getContext(), true);
        getActivity();
        break;

      case STATE_REGISTERED:
        getActivity();
        EspressoUtil.waitOn(RegistrationActivity.class);
        RegistrationActivityActions.enterPstnNumber(pstnCountry, pstnNumber);
        sleepThroughRegistrationLimit();
        RegistrationActivityActions.clickRegister();
        RegistrationActivityActions.clickContinue();
        EspressoUtil.waitOn(RegistrationProgressActivity.class);
        RegistrationActivityActions.sleepTillRegistrationConnected();
        RegistrationBypassUtil.receiveVerificationSms(getContext(), pstnCountry, pstnNumber, verificationCode);
        break;

      default:
        getActivity();
    }

    EspressoUtil.waitOn(clazz);
  }

  private void initMockPstnState() throws Exception {
    final Context context    = getInstrumentation().getContext();
    final String  pstnString = RegistrationBypassUtil.getPstnStringForDevice(context);

    pstnCountry      = pstnString.split(":")[0].replace("+", "");
    pstnNumber       = pstnString.split(":")[1];
    verificationCode = RegistrationBypassUtil.getVerificationCodeForPstnString(context, pstnString);

    Log.d(TAG, "using pstn string of " + pstnString);
  }

  @Override
  public void setUp() throws Exception {
    System.setProperty("dexmaker.dexcache", getContext().getCacheDir().getPath());
    super.setUp();

    if (pstnCountry == null || pstnNumber == null || verificationCode == null) {
      initMockPstnState();
    }
    initBaseState();
  }

  @Override
  public void tearDown() throws Exception {
    EspressoUtil.actuallyCloseSoftKeyboard();
    EspressoUtil.closeAllActivities();
    super.tearDown();
  }

}
