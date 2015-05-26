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
import static android.support.test.espresso.matcher.ViewMatchers.withId;

public class PassphraseChangeActivityActions {

  public static void clickOk() throws Exception {
    onView(withId(R.id.ok_button)).perform(click());
  }

  public static void clickCancel() throws Exception {
    onView(withId(R.id.cancel_button)).perform(click());
  }

  public static void typeNewPassphrase(String passphrase, String repeat) throws Exception {
    EspressoUtil.typeTextAndCloseKeyboard(onView(withId(R.id.new_passphrase)), passphrase);
    EspressoUtil.typeTextAndCloseKeyboard(onView(withId(R.id.repeat_passphrase)), repeat);
  }

  public static void typeNewPassphrase(String passphrase) throws Exception {
    typeNewPassphrase(passphrase, passphrase);
  }

  public static void typeChangePassphrase(String oldPassphrase, String newPassphrase) throws Exception {
    EspressoUtil.typeTextAndCloseKeyboard(onView(withId(R.id.old_passphrase)), oldPassphrase);
    EspressoUtil.typeTextAndCloseKeyboard(onView(withId(R.id.new_passphrase)), newPassphrase);
    EspressoUtil.typeTextAndCloseKeyboard(onView(withId(R.id.repeat_passphrase)), newPassphrase);
  }

}
