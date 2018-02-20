package org.thoughtcrime.securesms.espresso;

import org.thoughtcrime.securesms.R;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.action.ViewActions.typeText;
import static android.support.test.espresso.matcher.ViewMatchers.withId;

public class ConversationsHelper extends Helper<ConversationsHelper> {
    public ConversationsHelper(HelperSecret s) {}

    /* NAVIGATION */

    public ConversationHelper goConversation() {
        onView(withId(R.id.fab))
                .perform(click());
        onView(withId(R.id.search_view))
                .perform(typeText(phoneNumber));
        onView(withId(R.id.name))
                .perform(click());

        return new ConversationHelper(new HelperSecret());
    }
}
