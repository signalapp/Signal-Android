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

import android.support.test.espresso.matcher.ViewMatchers;
import android.test.suitebuilder.annotation.LargeTest;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static org.hamcrest.Matchers.not;

@LargeTest
public class RegistrationActivityTest extends TextSecureEspressoTestCase<ConversationListActivity> {

  public RegistrationActivityTest() {
    super(ConversationListActivity.class);
  }

  private void loadStateAndCheck() throws Exception {
    loadActivity(RegistrationActivity.class, STATE_BASE);
    onView(ViewMatchers.withId(R.id.skipButton)).check(matches(not(isDisplayed())));
    onView(withId(R.id.registerButton)).check(matches(isDisplayed()));
  }

  public void testRegister() throws Exception {
    loadStateAndCheck();
    RegistrationActivityActions.enterPstnNumber(pstnCountry, pstnNumber);
    sleepThroughRegistrationLimit();
    RegistrationActivityActions.clickRegister();
    RegistrationActivityActions.clickContinue();
    EspressoUtil.waitOn(RegistrationProgressActivity.class);
    RegistrationActivityActions.sleepTillRegistrationConnected();
    RegistrationBypassUtil.receiveVerificationSms(getContext(), pstnCountry, pstnNumber, verificationCode);
    EspressoUtil.waitOn(ConversationListActivity.class);
  }

}
