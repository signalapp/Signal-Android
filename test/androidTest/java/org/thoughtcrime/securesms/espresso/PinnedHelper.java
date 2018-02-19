package org.thoughtcrime.securesms.espresso;


import org.thoughtcrime.securesms.R;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.Espresso.pressBack;
import static android.support.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition;
import static android.support.test.espresso.matcher.ViewMatchers.withId;

public class PinnedHelper extends Helper<PinnedHelper> {
    public PinnedHelper(HelperSecret s) {}

    public PinnedHelper unpinMessage(int position) {
        onView(withId(android.R.id.list))
                .perform(actionOnItemAtPosition(position, ViewActions.clickChildViewWithId(R.id.unpin_button)));

        return this;
    }

    /* NAVIGATION */

    public ConversationHelper goConversation() {
        pressBack();

        return new ConversationHelper(new HelperSecret());
    }
}
