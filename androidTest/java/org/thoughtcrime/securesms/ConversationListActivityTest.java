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

import android.content.Context;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;

import org.thoughtcrime.securesms.components.DefaultSmsReminder;
import org.thoughtcrime.securesms.components.ExpiredBuildReminder;
import org.thoughtcrime.securesms.components.PushRegistrationReminder;
import org.thoughtcrime.securesms.components.SystemSmsImportReminder;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static android.support.test.espresso.Espresso.pressBack;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withContentDescription;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static android.support.test.espresso.action.ViewActions.click;

@LargeTest
public class ConversationListActivityTest extends SkipRegistrationInstrumentationTestCase {
  private final static String TAG = ConversationListActivityTest.class.getSimpleName();

  public ConversationListActivityTest() {
    super();
  }

  public ConversationListActivityTest(Context context) {
    super(context);
  }

  private void checkOptionsMenuItemsDisplayed() throws Exception {
    onView(withContentDescription(getContext().getString(R.string.conversation_list__menu_search)))
          .check(matches(isDisplayed()));

    openActionBarOverflowOrOptionsMenu(getContext());
    onView(withText(R.string.text_secure_normal__menu_new_group)).check(matches(isDisplayed()));
    onView(withText(R.string.text_secure_normal__mark_all_as_read)).check(matches(isDisplayed()));
    onView(withText(R.string.arrays__import_export)).check(matches(isDisplayed()));
    onView(withText(R.string.arrays__my_identity_key)).check(matches(isDisplayed()));
    onView(withText(R.string.text_secure_normal__menu_settings)).check(matches(isDisplayed()));

    if (!TextSecurePreferences.isPasswordDisabled(getContext())) {
      onView(withText(R.string.text_secure_normal__menu_clear_passphrase))
            .check(matches(isDisplayed()));
    }

    pressBack();
  }

  private boolean checkReminderIsDisplayed() throws Exception {
    boolean reminderVisible    = true;
    Integer reminderTitleResId = null;
    Integer reminderTextResId  = null;

    if (ExpiredBuildReminder.isEligible(getContext())) {
      reminderTitleResId = R.string.reminder_header_expired_build;
      reminderTextResId  = R.string.reminder_header_expired_build_details;
    } else if (DefaultSmsReminder.isEligible(getContext())) {
      reminderTitleResId = R.string.reminder_header_sms_default_title;
      reminderTextResId  = R.string.reminder_header_sms_default_text;
    } else if (SystemSmsImportReminder.isEligible(getContext())) {
      reminderTitleResId = R.string.reminder_header_sms_import_title;
      reminderTextResId  = R.string.reminder_header_sms_import_text;
    } else if (PushRegistrationReminder.isEligible(getContext())) {
      reminderTitleResId = R.string.reminder_header_push_title;
      reminderTextResId  = R.string.reminder_header_push_text;
    } else {
      reminderVisible = false;
    }

    if (reminderVisible) {
      onView(withId(R.id.reminder_title)).check(matches(isDisplayed()));
      onView(withId(R.id.reminder_title)).check(matches(withText(reminderTitleResId)));
      onView(withId(R.id.reminder_text)).check(matches(isDisplayed()));
      onView(withId(R.id.reminder_text)).check(matches(withText(reminderTextResId)));
    }

    return reminderVisible;
  }

  private void handleTestState() throws Exception {
    waitOn(ConversationListActivity.class);
    checkOptionsMenuItemsDisplayed();
    checkReminderIsDisplayed();
  }

  public void testDismissAllReminders() throws Exception {
    handleTestState();

    int expectedReminders = 0;
    if (ExpiredBuildReminder.isEligible(getContext()))    expectedReminders++;
    if (DefaultSmsReminder.isEligible(getContext()))      expectedReminders++;
    if (SystemSmsImportReminder.isEligible(getContext())) expectedReminders++;

    Log.d(TAG, "expecting to see " + expectedReminders + " reminders");
    while (expectedReminders > 0) {
      if (!checkReminderIsDisplayed()) {
        throw new IllegalStateException("expected to see " + expectedReminders + " more reminders");
      }

      Log.d(TAG, "found reminder, dismissing now");
      onView(withId(R.id.cancel)).perform(click());
      expectedReminders--;

      openActionBarOverflowOrOptionsMenu(getContext());
      onView(withText(R.string.text_secure_normal__menu_settings)).perform(click());
      pressBack();
    }

    if (checkReminderIsDisplayed() && !PushRegistrationReminder.isEligible(getContext())) {
      throw new IllegalStateException("only expected to see " + expectedReminders + " reminders");
    }
  }

  public void testClickNewGroup() throws Exception {
    handleTestState();

    openActionBarOverflowOrOptionsMenu(getContext());
    onView(withText(R.string.text_secure_normal__menu_new_group)).perform(click());
  }

  public void testClickImportExport() throws Exception {
    handleTestState();

    openActionBarOverflowOrOptionsMenu(getContext());
    onView(withText(R.string.arrays__import_export)).perform(click());
  }

  public void testClickMyIdentity() throws Exception {
    handleTestState();

    openActionBarOverflowOrOptionsMenu(getContext());
    onView(withText(R.string.arrays__my_identity_key)).perform(click());
  }

  public void testClickSettings() throws Exception {
    handleTestState();

    openActionBarOverflowOrOptionsMenu(getContext());
    onView(withText(R.string.text_secure_normal__menu_settings)).perform(click());
  }

}
