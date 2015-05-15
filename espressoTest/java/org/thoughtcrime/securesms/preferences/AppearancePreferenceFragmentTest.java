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

import android.test.suitebuilder.annotation.LargeTest;

import org.hamcrest.Matchers;
import org.thoughtcrime.securesms.ConversationListActivity;
import org.thoughtcrime.securesms.TextSecureEspressoTestCase;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import static android.support.test.espresso.Espresso.onData;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.PreferenceMatchers.withKey;
import static android.support.test.espresso.matcher.PreferenceMatchers.withSummaryText;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;

@LargeTest
public class AppearancePreferenceFragmentTest extends TextSecureEspressoTestCase<ConversationListActivity> {

  public AppearancePreferenceFragmentTest() {
    super(ConversationListActivity.class);
  }

  private void checkAllPreferencesDisplayed() throws Exception {
    onData(Matchers.<Object>allOf(withKey("pref_theme")))
          .check(matches(isDisplayed()));
    onData(Matchers.<Object>allOf(withKey("pref_language")))
          .check(matches(isDisplayed()));
  }

  private void checkViewsMatchPreferences() throws Exception {
    // todo :|
    final String theme = TextSecurePreferences.getTheme(getContext());
    onData(Matchers.<Object>allOf(withKey("pref_theme"), withSummaryText(theme)))
          .check(matches(isDisplayed()));

    final String language = TextSecurePreferences.getTheme(getContext());
    onData(Matchers.<Object>allOf(withKey("pref_language"), withSummaryText(language)))
        .check(matches(isDisplayed()));
  }

}
