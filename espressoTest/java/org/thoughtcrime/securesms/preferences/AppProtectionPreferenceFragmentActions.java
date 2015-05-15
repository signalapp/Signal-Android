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

import org.hamcrest.Matchers;
import org.thoughtcrime.securesms.R;

import static android.support.test.espresso.Espresso.onData;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.matcher.PreferenceMatchers.withKey;
import static android.support.test.espresso.matcher.ViewMatchers.withText;

public class AppProtectionPreferenceFragmentActions {

  public static void clickEnablePassphrase() throws Exception {
    onData(Matchers.<Object>allOf(withKey("pref_enable_passphrase_temporary"))).perform(click());
  }

  public static void disablePassphrase() throws Exception {
    clickEnablePassphrase();
    onView(withText(R.string.ApplicationPreferencesActivity_disable)).perform(click());
  }

  public static void clickChangePassphrase() throws Exception {
    onData(Matchers.<Object>allOf(withKey("pref_change_passphrase"))).perform(click());
  }

  public static void clickTimeoutPassphrase() throws Exception {
    onData(Matchers.<Object>allOf(withKey("pref_timeout_passphrase"))).perform(click());
  }

  public static void clickScreenSecurity() throws Exception {
    onData(Matchers.<Object>allOf(withKey("pref_screen_security"))).perform(click());
  }

}
