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

import org.hamcrest.Matchers;

import static android.support.test.espresso.Espresso.onData;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.matcher.PreferenceMatchers.withKey;

public class ApplicationPreferencesActivityActions {

  public static void clickSmsAndMmsSetting() throws Exception {
    onData(Matchers.<Object>allOf(withKey("preference_category_sms_mms"))).perform(click());
  }

  public static void clickNotificationsSetting() throws Exception {
    onData(Matchers.<Object>allOf(withKey("preference_category_notifications"))).perform(click());
  }

  public static void clickAppProtectionSetting() throws Exception {
    onData(Matchers.<Object>allOf(withKey("preference_category_app_protection"))).perform(click());
  }

  public static void clickAppearanceSetting() throws Exception {
    onData(Matchers.<Object>allOf(withKey("preference_category_appearance"))).perform(click());
  }

  public static void clickDeleteOldMessagesSetting() throws Exception {
    onData(Matchers.<Object>allOf(withKey("preference_category_storage"))).perform(click());
  }

  public static void clickAdvancedSetting() throws Exception {
    onData(Matchers.<Object>allOf(withKey("preference_category_advanced"))).perform(click());
  }

}
