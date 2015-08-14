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
import android.util.Log;
import android.widget.TextView;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.thoughtcrime.securesms.components.DefaultSmsReminder;
import org.thoughtcrime.securesms.components.ExpiredBuildReminder;
import org.thoughtcrime.securesms.components.PushRegistrationReminder;
import org.thoughtcrime.securesms.components.SystemSmsImportReminder;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static android.support.test.espresso.Espresso.pressBack;
import static android.support.test.espresso.action.ViewActions.longClick;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withContentDescription;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.thoughtcrime.securesms.EspressoUtil.addContact;
import static org.thoughtcrime.securesms.EspressoUtil.waitOn;
import static org.thoughtcrime.securesms.ViewMatchers.withRecyclerItem;
import static org.thoughtcrime.securesms.ViewMatchers.withRecyclerItemCount;

@LargeTest
public class ConversationListActivityTest extends TextSecureEspressoTestCase<ConversationListActivity> {
  private final static String TAG = ConversationListActivityTest.class.getSimpleName();

  public ConversationListActivityTest() {
    super(ConversationListActivity.class);
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

    if (ExpiredBuildReminder.isEligible()) {
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

  private void loadAndCheckState() throws Exception {
    loadActivity(ConversationListActivity.class, STATE_REGISTRATION_SKIPPED);
    checkOptionsMenuItemsDisplayed();
    checkReminderIsDisplayed();
  }

  public void testDismissAllReminders() throws Exception {
    loadAndCheckState();

    int expectedReminders = 0;
    if (ExpiredBuildReminder.isEligible())    expectedReminders++;
    if (DefaultSmsReminder.isEligible(getContext()))      expectedReminders++;
    if (SystemSmsImportReminder.isEligible(getContext())) expectedReminders++;

    Log.d(TAG, "expecting to see " + expectedReminders + " reminders");
    while (expectedReminders > 0) {
      if (!checkReminderIsDisplayed()) {
        throw new IllegalStateException("expected to see " + expectedReminders + " more reminders");
      }

      Log.d(TAG, "found reminder, dismissing now");
      ConversationListActivityActions.dismissReminder();
      expectedReminders--;

      ConversationListActivityActions.clickSettings(getContext());
      waitOn(ApplicationPreferencesActivity.class);
      pressBack();
      waitOn(ConversationListActivity.class);
    }

    if (checkReminderIsDisplayed() && !PushRegistrationReminder.isEligible(getContext())) {
      throw new IllegalStateException("only expected to see " + expectedReminders + " reminders");
    }
  }

  public void testClickNewConversation() throws Exception {
    loadAndCheckState();
    ConversationListActivityActions.clickNewConversation();
    waitOn(NewConversationActivity.class);
  }

  public void testClickNewGroup() throws Exception {
    loadAndCheckState();
    ConversationListActivityActions.clickNewGroup(getContext());
    waitOn(GroupCreateActivity.class);
  }

  public void testClickImportExport() throws Exception {
    loadAndCheckState();
    ConversationListActivityActions.clickImportExport(getContext());
    waitOn(ImportExportActivity.class);
  }

  public void testClickMyIdentity() throws Exception {
    loadAndCheckState();
    ConversationListActivityActions.clickMyIdentity(getContext());
    waitOn(ViewLocalIdentityActivity.class);
  }

  public void testClickSettings() throws Exception {
    loadAndCheckState();
    ConversationListActivityActions.clickSettings(getContext());
    waitOn(ApplicationPreferencesActivity.class);
  }

  public static Matcher<Object> isThreadFrom(final String contactName) {
    return new TypeSafeMatcher<Object>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("is thread from: " + contactName);
      }

      @Override
      public boolean matchesSafely(Object object) {
        if (!(object instanceof ConversationListItem)) {
          return false;
        }

        ConversationListItem itemView = (ConversationListItem) object;
        TextView             fromView = (TextView) itemView.findViewById(R.id.from);
        return fromView.getText().toString().equals(contactName);
      }
    };
  }

  public static Matcher<Object> withThreadSnippet(final String snippet) {
    return new TypeSafeMatcher<Object>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("is thread with snippet: " + snippet);
      }

      @Override
      public boolean matchesSafely(Object object) {
        if (!(object instanceof ConversationListItem)) {
          return false;
        }

        ConversationListItem itemView    = (ConversationListItem) object;
        TextView             snippetView = (TextView) itemView.findViewById(R.id.subject);
        return snippetView.getText().toString().equals(snippet);
      }
    };
  }

  @SuppressWarnings("unchecked")
  public void testSaveTextDraft() throws Exception {
    final String CONTACT_NAME   = "Clement Duval";
    final String CONTACT_NUMBER = "55555555555";
    final String DRAFT_MESSAGE  = "I struck him in the name of liberty";
    final String DRAFT_SNIPPET  = getContext().getString(R.string.ThreadRecord_draft) + " " + DRAFT_MESSAGE;

    addContact(getContext(), CONTACT_NAME, CONTACT_NUMBER);
    loadAndCheckState();

    ConversationListActivityActions.clickNewConversation();
    waitOn(NewConversationActivity.class);
    NewConversationActivityActions.clickContactWithName(CONTACT_NAME);
    waitOn(ConversationActivity.class);
    ConversationActivityActions.typeMessage(DRAFT_MESSAGE);
    pressBack();
    waitOn(ConversationListActivity.class);

    onView(withId(R.id.list)).check(matches(
        withRecyclerItemCount(1L)
    ));
    onView(withId(R.id.list)).check(matches(
        withRecyclerItem(allOf(
            isDisplayed(),
            isThreadFrom(CONTACT_NAME),
            withThreadSnippet(DRAFT_SNIPPET)))
    ));
  }

  /*
  this is known to fail on some older devices due to some espresso
  related app-compat bug
   */
  @SuppressWarnings("unchecked")
  public void testSaveDeleteTextDraft() throws Exception {
    final String CONTACT_NAME   = "Clement Duval";
    final String CONTACT_NUMBER = "55555555555";
    final String DRAFT_MESSAGE  = "I struck him in the name of liberty";
    final String DRAFT_SNIPPET  = getContext().getString(R.string.ThreadRecord_draft) + " " + DRAFT_MESSAGE;

    addContact(getContext(), CONTACT_NAME, CONTACT_NUMBER);
    loadAndCheckState();

    ConversationListActivityActions.clickNewConversation();
    waitOn(NewConversationActivity.class);
    NewConversationActivityActions.clickContactWithName(CONTACT_NAME);
    waitOn(ConversationActivity.class);
    ConversationActivityActions.typeMessage(DRAFT_MESSAGE);
    pressBack();
    waitOn(ConversationListActivity.class);

    onView(withId(R.id.list)).check(matches(
        withRecyclerItemCount(1L)
    ));
    onView(withId(R.id.list)).check(matches(
        withRecyclerItem(allOf(
            isDisplayed(),
            isThreadFrom(CONTACT_NAME),
            withThreadSnippet(DRAFT_SNIPPET)))
    ));

    onView(withText(CONTACT_NAME)).perform(longClick());
    ConversationListActivityActions.deleteSelected();

    onView(withId(R.id.list)).check(matches(
        withRecyclerItemCount(0L)
    ));
  }

}
