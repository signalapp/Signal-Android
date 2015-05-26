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

import android.test.suitebuilder.annotation.LargeTest;

import org.hamcrest.Matchers;

import static android.support.test.espresso.Espresso.onData;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.PreferenceMatchers.withKey;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static org.thoughtcrime.securesms.EspressoUtil.waitOn;

@LargeTest
public class ApplicationPreferencesActivityTest extends TextSecureEspressoTestCase<ConversationListActivity> {

  public ApplicationPreferencesActivityTest() {
    super(ConversationListActivity.class);
  }

  private void checkAllPreferencesDisplayed() throws Exception {
    onData(Matchers.<Object>allOf(withKey("preference_category_sms_mms")))
          .check(matches(isDisplayed()));
    onData(Matchers.<Object>allOf(withKey("preference_category_notifications")))
          .check(matches(isDisplayed()));
    onData(Matchers.<Object>allOf(withKey("preference_category_app_protection")))
          .check(matches(isDisplayed()));
    onData(Matchers.<Object>allOf(withKey("preference_category_appearance")))
          .check(matches(isDisplayed()));
    onData(Matchers.<Object>allOf(withKey("preference_category_storage")))
          .check(matches(isDisplayed()));
    onData(Matchers.<Object>allOf(withKey("preference_category_advanced")))
          .check(matches(isDisplayed()));
  }

  public void testClickSettings() throws Exception {
    loadActivity(ConversationListActivity.class, STATE_REGISTRATION_SKIPPED);
    ConversationListActivityActions.clickSettings(getContext());
    waitOn(ApplicationPreferencesActivity.class);
    checkAllPreferencesDisplayed();
  }

}
