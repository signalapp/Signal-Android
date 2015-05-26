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

import org.thoughtcrime.securesms.preferences.AppProtectionPreferenceFragmentActions;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static org.hamcrest.Matchers.not;

@LargeTest
public class PassphraseChangeActivityTest extends TextSecureEspressoTestCase<ConversationListActivity> {

  private static final String ORIGINAL_PASSPHRASE = "badpass";
  private static final String CHANGE_PASSPHRASE   = "worse";

  public PassphraseChangeActivityTest() {
    super(ConversationListActivity.class);
  }

  private void checkState() throws Exception {
    EspressoUtil.actuallyCloseSoftKeyboard();

    if (TextSecurePreferences.isPasswordDisabled(getContext())) {
      onView(withId(R.id.old_passphrase)).check(matches(not(isDisplayed())));
      onView(withId(R.id.old_passphrase_label)).check(matches(not(isDisplayed())));
    } else {
      onView(withId(R.id.old_passphrase)).check(matches(isDisplayed()));
      onView(withId(R.id.old_passphrase_label)).check(matches(isDisplayed()));
    }

    onView(withId(R.id.new_passphrase)).check(matches(isDisplayed()));
    onView(withId(R.id.repeat_passphrase)).check(matches(isDisplayed()));
  }

  private void clickEnablePassphraseAndCheckState() throws Exception {
    loadActivity(ConversationListActivity.class, STATE_REGISTRATION_SKIPPED);
    ConversationListActivityActions.clickSettings(getContext());
    EspressoUtil.waitOn(ApplicationPreferencesActivity.class);
    ApplicationPreferencesActivityActions.clickAppProtectionSetting();
    AppProtectionPreferenceFragmentActions.clickEnablePassphrase();
    EspressoUtil.waitOn(PassphraseChangeActivity.class);
    checkState();
  }

  public void testEnablePassphrase() throws Exception {
    clickEnablePassphraseAndCheckState();
    PassphraseChangeActivityActions.typeNewPassphrase(ORIGINAL_PASSPHRASE);
    PassphraseChangeActivityActions.clickOk();

    EspressoUtil.waitOn(ApplicationPreferencesActivity.class);
    assertTrue(!TextSecurePreferences.isPasswordDisabled(getContext()));
  }

  public void testEnablePassphraseCancel() throws Exception {
    clickEnablePassphraseAndCheckState();
    EspressoUtil.actuallyCloseSoftKeyboard();
    PassphraseChangeActivityActions.clickCancel();

    EspressoUtil.waitOn(ApplicationPreferencesActivity.class);
    assertTrue(TextSecurePreferences.isPasswordDisabled(getContext()));
  }

  public void testEnablePassphraseDoesNotMatch() throws Exception {
    clickEnablePassphraseAndCheckState();
    PassphraseChangeActivityActions.typeNewPassphrase(ORIGINAL_PASSPHRASE, "nope");
    PassphraseChangeActivityActions.clickOk();
    PassphraseChangeActivityActions.clickCancel();

    EspressoUtil.waitOn(ApplicationPreferencesActivity.class);
    assertTrue(TextSecurePreferences.isPasswordDisabled(getContext()));
  }

  private void clickChangePassphraseAndCheckState() throws Exception {
    testEnablePassphrase();
    AppProtectionPreferenceFragmentActions.clickChangePassphrase();
    EspressoUtil.waitOn(PassphraseChangeActivity.class);
    checkState();
  }

  public void testChangePassphrase() throws Exception {
    clickChangePassphraseAndCheckState();
    PassphraseChangeActivityActions.typeChangePassphrase(ORIGINAL_PASSPHRASE, CHANGE_PASSPHRASE);
    PassphraseChangeActivityActions.clickOk();

    EspressoUtil.waitOn(ApplicationPreferencesActivity.class);
    assertTrue(!TextSecurePreferences.isPasswordDisabled(getContext()));
  }

  public void testChangePassphraseCancel() throws Exception {
    clickChangePassphraseAndCheckState();

    EspressoUtil.actuallyCloseSoftKeyboard();
    PassphraseChangeActivityActions.clickCancel();

    EspressoUtil.waitOn(ApplicationPreferencesActivity.class);
    assertTrue(!TextSecurePreferences.isPasswordDisabled(getContext()));
  }

}
