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

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;

public class RegistrationActivityActions {

  public static void typeCountryCode(String countryCode) throws Exception {
    EspressoUtil.replaceTextAndCloseKeyboard(onView(withId(R.id.country_code)), countryCode);
  }

  public static void typeLocalNumber(String localNumber) throws Exception {
    EspressoUtil.replaceTextAndCloseKeyboard(onView(withId(R.id.number)), localNumber);
  }

  public static void clickRegister() throws Exception {
    onView(withId(R.id.registerButton)).perform(click());
  }

  public static void clickCancel() throws Exception {
    onView(withId(R.id.skipButton)).perform(click());
  }

  public static void clickContinue() throws Exception {
    onView(withText(R.string.RegistrationActivity_continue)).perform(click());
  }

  public static void clickEdit() throws Exception {
    onView(withText(R.string.RegistrationActivity_edit)).perform(click());
  }

  public static void enterPstnNumber(String pstnCountry, String pstnNumber) throws Exception {
    typeCountryCode(pstnCountry);
    typeLocalNumber(pstnNumber);
  }

  public static void sleepTillRegistrationConnected() {
    long timeout = 0L;
    while (timeout < 10000L) {
      try {

        Thread.sleep(1000);
        timeout += 1000;
        onView(withId(R.id.verification_progress)).check(matches(isDisplayed()));
        return;

      } catch (InterruptedException e) { }
        catch (AssertionError e)       { }
    }
    throw new AssertionError("failed to connect to registration servers within 10 seconds");
  }

}
