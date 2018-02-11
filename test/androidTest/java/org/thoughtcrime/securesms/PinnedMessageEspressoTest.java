package org.thoughtcrime.securesms;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.Intent;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.rule.ActivityTestRule;
import android.support.test.filters.LargeTest;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static android.support.test.espresso.assertion.ViewAssertions.matches;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class PinnedMessageEspressoTest {

    @Rule
    public ActivityTestRule<PinnedMessageActivity> pinnedActivityRule =
            new ActivityTestRule(PinnedMessageActivity.class, true, false);

    @Test
    public void pageExists() {
        Intent intent = new Intent();

        intent.putExtra("address", "514 123 4567");
        intent.putExtra("THREADID", 1);

        pinnedActivityRule.launchActivity(intent);

        // onView(withId(R.id.pinned_message_body)).check(matches(isDisplayed()));

        onView(withText("Signal needs access to your contacts and media in order to connect with friends, exchange messages, and make secure calls")).check(matches(isDisplayed()));
    }
}