package org.thoughtcrime.securesms;

import android.test.suitebuilder.annotation.LargeTest;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.action.ViewActions.longClick;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static org.thoughtcrime.securesms.EspressoUtil.addContact;
import static org.thoughtcrime.securesms.EspressoUtil.waitOn;

/**
 * rhodey
 */
@LargeTest
public class ConversationActivityTest extends TextSecureEspressoTestCase<ConversationListActivity> {

  public ConversationActivityTest() {
    super(ConversationListActivity.class);
  }

  public void testForwardMessage() throws Exception {
    final String[] CONTACT_NAMES   = new String[] {"Clement Duval", "Masha Kolenkia"};
    final String[] CONTACT_NUMBERS = new String[] {"55555555555",   "33333333333"};
    final String   MESSAGE         = "I struck him in the name of liberty";

    addContact(getContext(), CONTACT_NAMES[0], CONTACT_NUMBERS[0]);
    addContact(getContext(), CONTACT_NAMES[1], CONTACT_NUMBERS[1]);
    loadActivity(ConversationListActivity.class, STATE_REGISTERED);

    ConversationListActivityActions.clickNewConversation();
    waitOn(NewConversationActivity.class);
    NewConversationActivityActions.clickContactWithName(CONTACT_NAMES[0]);
    waitOn(ConversationActivity.class);
    ConversationActivityActions.typeMessage(MESSAGE);
    ConversationActivityActions.clickSend();

    onView(withText(MESSAGE)).perform(longClick());
    ConversationActivityActions.clickForwardMessage();

    waitOn(ShareActivity.class);
    onView(withId(R.id.menu_new_message)).perform(click());
    waitOn(NewConversationActivity.class);
    NewConversationActivityActions.filterNameOrNumber(CONTACT_NAMES[1]);
    NewConversationActivityActions.clickContactWithName(CONTACT_NAMES[1]);

    waitOn(ConversationActivity.class);
    onView(withText(MESSAGE)).check(matches(isDisplayed()));
  }

}
