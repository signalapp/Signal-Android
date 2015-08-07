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

import static android.support.test.espresso.Espresso.onData;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.matcher.PreferenceMatchers.withKey;

public class AppearancePreferenceFragmentActions {

  public static void clickTheme() throws Exception {
    onData(Matchers.<Object>allOf(withKey("pref_theme"))).perform(click());
  }

  public static void clickLanguage() throws Exception {
    onData(Matchers.<Object>allOf(withKey("pref_language"))).perform(click());
  }

  public static void clickEmoji() throws Exception {
    onData(Matchers.<Object>allOf(withKey("pref_emoji"))).perform(click());
  }

}
