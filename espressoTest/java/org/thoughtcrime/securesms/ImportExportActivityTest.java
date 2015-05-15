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
import static android.support.test.espresso.action.ViewActions.swipeLeft;
import static android.support.test.espresso.action.ViewActions.swipeRight;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;

@LargeTest
public class ImportExportActivityTest extends TextSecureEspressoTestCase<ConversationListActivity> {

  public ImportExportActivityTest() {
    super(ConversationListActivity.class);
  }

  private void checkImportLayout() throws Exception {
    onView(ViewMatchers.withId(R.id.import_sms)).check(matches(isDisplayed()));
    onView(withId(R.id.import_encrypted_backup)).check(matches(isDisplayed()));
    onView(withId(R.id.import_plaintext_backup)).check(matches(isDisplayed()));
  }

  private void checkExportLayout() throws Exception {
    onView(withId(R.id.export_plaintext_backup)).check(matches(isDisplayed()));
  }

  private void clickImportExport() throws Exception {
    loadActivity(ConversationListActivity.class, STATE_REGISTRATION_SKIPPED);
    ConversationListActivityActions.clickImportExport(getContext());
    EspressoUtil.waitOn(ImportExportActivity.class);
  }

  public void testLayout() throws Exception {
    clickImportExport();
    checkImportLayout();
    onView(withId(R.id.import_sms)).perform(swipeLeft());
    checkExportLayout();
    onView(withId(R.id.export_plaintext_backup)).perform(swipeRight());
    checkImportLayout();
  }

}
