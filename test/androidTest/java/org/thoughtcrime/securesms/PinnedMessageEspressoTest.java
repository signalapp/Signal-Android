package org.thoughtcrime.securesms;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.support.test.runner.AndroidJUnit4;
import android.support.test.rule.ActivityTestRule;
import android.support.test.filters.LargeTest;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.matcher.ViewMatchers.withText;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class PinnedMessageEspressoTest {

    @Rule
    public ActivityTestRule<ConversationListActivity> mainActivityRule =
            new ActivityTestRule(ConversationListActivity.class, true, false);

    @Test
    public void pageExists() {
        EspressoHelper helper = new EspressoHelper(mainActivityRule);

        helper
            .createConversation()
            .goPinned()
            .done();
    }

    @Test
    public void canPinMessages() {
        EspressoHelper helper = new EspressoHelper(mainActivityRule);

        String testString = helper.randString();

        helper
            .createConversation()
                .sendMessage(testString)
                .selectMessage(1)
                .pinSelectedMessage()
            .goPinned()
                .findText(testString)
            .done();
    }


    @Test
    public void canUnPinMessages() {
        EspressoHelper helper = new EspressoHelper(mainActivityRule);

        String testString = helper.randString();

        helper
            .createConversation()
                .sendMessage(testString)
                .selectMessage()
                .pinSelectedMessage()
            .goPinned()
                .findText(testString)
            .goConversation()
                .selectMessage()
                .unpinSelectedMessage()
            .goPinned()
                .not_findText(testString)
            .done();
    }

}