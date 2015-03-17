package org.thoughtcrime.securesms;

import android.test.suitebuilder.annotation.LargeTest;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;

/**
 * rhodey
 */
@LargeTest
public class GroupCreateActivityTest extends SkipRegistrationInstrumentationTestCase {

  public GroupCreateActivityTest() {
    super();
  }

  public void testLayout() throws Exception {
    new ConversationListActivityTest(getContext()).testClickNewGroup();
    waitOn(GroupCreateActivity.class);

    onView(withId(R.id.push_disabled)).check(matches(isDisplayed()));
    onView(withId(R.id.push_disabled_reason))
          .check(matches(withText(R.string.GroupCreateActivity_you_dont_support_push)));

    waitOn(GroupCreateActivity.class);
  }

}
