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
import org.thoughtcrime.securesms.ApplicationPreferencesActivityTest;
import org.thoughtcrime.securesms.PassphraseChangeActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.SkipRegistrationInstrumentationTestCase;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import static android.support.test.espresso.Espresso.onData;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.PreferenceMatchers.withKey;
import static android.support.test.espresso.matcher.ViewMatchers.isChecked;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.isNotChecked;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;

@LargeTest
public class AppProtectionPreferenceFragmentTest extends SkipRegistrationInstrumentationTestCase {

  public AppProtectionPreferenceFragmentTest() {
    super();
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

  private void clickAppProtectionSettingAndTestState() throws Exception {
    new ApplicationPreferencesActivityTest(getContext()).testClickAppProtectionSetting();
    checkAllPreferencesDisplayed();
    checkViewsMatchPreferences();
  }

  public void testEnablePassphrase() throws Exception {
    clickAppProtectionSettingAndTestState();

    onData(Matchers.<Object>allOf(withKey("pref_enable_passphrase_temporary"))).perform(click());
    waitOn(PassphraseChangeActivity.class);
    typeTextAndClose(onView(withId(R.id.new_passphrase)),    "bad_pass");
    typeTextAndClose(onView(withId(R.id.repeat_passphrase)), "bad_pass");
    onView(withId(R.id.ok_button)).perform(click());

    waitOn(ApplicationPreferencesActivity.class);
    isChecked().matches(onData(Matchers.<Object>allOf(withKey("pref_enable_passphrase_temporary"))));
  }

  public void testChangePassphrase() throws Exception {
    testEnablePassphrase();

    onData(Matchers.<Object>allOf(withKey("pref_change_passphrase"))).perform(click());
    waitOn(PassphraseChangeActivity.class);
    typeTextAndClose(onView(withId(R.id.old_passphrase)),    "bad_pass");
    typeTextAndClose(onView(withId(R.id.new_passphrase)),    "still_bad_pass");
    typeTextAndClose(onView(withId(R.id.repeat_passphrase)), "still_bad_pass");
    onView(withId(R.id.ok_button)).perform(click());

    waitOn(ApplicationPreferencesActivity.class);
    isChecked().matches(onData(Matchers.<Object>allOf(withKey("pref_enable_passphrase_temporary"))));
  }

  public void testDisablePassphrase() throws Exception {
    testEnablePassphrase();

    onData(Matchers.<Object>allOf(withKey("pref_enable_passphrase_temporary"))).perform(click());
    onView(withText(R.string.ApplicationPreferencesActivity_disable)).perform(click());
    isNotChecked().matches(onData(Matchers.<Object>allOf(withKey("pref_enable_passphrase_temporary"))));
  }

}
