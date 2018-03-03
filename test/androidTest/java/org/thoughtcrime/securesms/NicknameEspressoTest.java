package org.thoughtcrime.securesms;

import android.support.test.filters.LargeTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.thoughtcrime.securesms.espresso.Helper;


@RunWith(AndroidJUnit4.class)
@LargeTest
public class NicknameEspressoTest {
    @Rule
    public ActivityTestRule<RecipientPreferenceActivity> mainActivityRule =
            new ActivityTestRule(RecipientPreferenceActivity.class, true, false);

    public ActivityTestRule<ConversationListActivity> otherActivityRule =
            new ActivityTestRule(ConversationListActivity.class, true, false);

    @Test
    public void pageExists(){
        Helper helper = new Helper(mainActivityRule);
        Helper otherHelper = new Helper(otherActivityRule);

        otherHelper
                .goConversations()
                .goConversation()
                .goSettings();

    }

    public void




}
