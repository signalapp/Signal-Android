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
package org.thoughtcrime.securesms.preferences;

import android.os.Build;
import android.test.suitebuilder.annotation.LargeTest;

import org.hamcrest.Matchers;
import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.ConversationListActivity;
import org.thoughtcrime.securesms.PassphraseChangeActivity;
import org.thoughtcrime.securesms.ApplicationPreferencesActivityActions;
import org.thoughtcrime.securesms.ConversationListActivityActions;
import org.thoughtcrime.securesms.PassphraseChangeActivityActions;
import org.thoughtcrime.securesms.TextSecureEspressoTestCase;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import static android.support.test.espresso.Espresso.onData;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.PreferenceMatchers.withKey;
import static android.support.test.espresso.matcher.ViewMatchers.isChecked;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.isNotChecked;
import static org.thoughtcrime.securesms.EspressoUtil.waitOn;

@LargeTest
public class AppProtectionPreferenceFragmentTest extends TextSecureEspressoTestCase<ConversationListActivity> {

  private static final String ORIGINAL_PASSPHRASE = "badpass";

  public AppProtectionPreferenceFragmentTest() {
    super(ConversationListActivity.class);
  }

  private void checkAllPreferencesDisplayed() throws Exception {
    onData(Matchers.<Object>allOf(withKey("pref_enable_passphrase_temporary"))).check(matches(isDisplayed()));
    onData(Matchers.<Object>allOf(withKey("pref_change_passphrase"))).check(matches(isDisplayed()));
    onData(Matchers.<Object>allOf(withKey("pref_timeout_passphrase"))).check(matches(isDisplayed()));
    onData(Matchers.<Object>allOf(withKey("pref_timeout_interval"))).check(matches(isDisplayed()));

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
      onData(Matchers.<Object>allOf(withKey("pref_screen_security")))
            .check(matches(isDisplayed()));
    }
  }

  private void checkViewsMatchPreferences() throws Exception {
    if (!TextSecurePreferences.isPasswordDisabled(getContext())) {
      isChecked().matches(onData(Matchers.<Object>allOf(withKey("pref_enable_passphrase_temporary"))));
    } else {
      isNotChecked().matches(onData(Matchers.<Object>allOf(withKey("pref_enable_passphrase_temporary"))));
    }

    if (TextSecurePreferences.isPassphraseTimeoutEnabled(getContext())) {
      isChecked().matches(onData(Matchers.<Object>allOf(withKey("pref_timeout_passphrase"))));
    } else {
      isNotChecked().matches(onData(Matchers.<Object>allOf(withKey("pref_timeout_passphrase"))));
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
      if (TextSecurePreferences.isScreenSecurityEnabled(getContext())) {
        isChecked().matches(onData(Matchers.<Object>allOf(withKey("pref_screen_security"))));
      } else {
        isNotChecked().matches(onData(Matchers.<Object>allOf(withKey("pref_screen_security"))));
      }
    }
  }

  private void checkState() throws Exception {
    checkAllPreferencesDisplayed();
    checkViewsMatchPreferences();
  }

  private void clickAppProtectionSettingAndCheckState() throws Exception {
    loadActivity(ConversationListActivity.class, STATE_REGISTRATION_SKIPPED);
    ConversationListActivityActions.clickSettings(getContext());
    waitOn(ApplicationPreferencesActivity.class);
    ApplicationPreferencesActivityActions.clickAppProtectionSetting();
    checkState();
  }

  public void testEnablePassphrase() throws Exception {
    clickAppProtectionSettingAndCheckState();

    AppProtectionPreferenceFragmentActions.clickEnablePassphrase();
    waitOn(PassphraseChangeActivity.class);
    PassphraseChangeActivityActions.typeNewPassphrase(ORIGINAL_PASSPHRASE);
    PassphraseChangeActivityActions.clickOk();
    waitOn(ApplicationPreferencesActivity.class);

    isChecked().matches(onData(Matchers.<Object>allOf(withKey("pref_enable_passphrase_temporary"))));
    checkState();
  }

  public void testDisablePassphrase() throws Exception {
    testEnablePassphrase();
    AppProtectionPreferenceFragmentActions.disablePassphrase();

    isNotChecked().matches(onData(Matchers.<Object>allOf(withKey("pref_enable_passphrase_temporary"))));
    checkState();
  }

  public void testEnablePassphraseTimeout() throws Exception {
    testEnablePassphrase();
    AppProtectionPreferenceFragmentActions.clickTimeoutPassphrase();

    isChecked().matches(onData(Matchers.<Object>allOf(withKey("pref_timeout_passphrase"))));
    checkState();
  }

  public void testDisableScreenSecurity() throws Exception {
    clickAppProtectionSettingAndCheckState();
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
      return;
    }

    AppProtectionPreferenceFragmentActions.clickScreenSecurity();
    isNotChecked().matches(onData(Matchers.<Object>allOf(withKey("pref_screen_security"))));
    checkState();
  }

}
