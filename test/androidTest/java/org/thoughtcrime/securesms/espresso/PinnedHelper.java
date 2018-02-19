package org.thoughtcrime.securesms.espresso;


import static android.support.test.espresso.Espresso.pressBack;

public class PinnedHelper extends Helper<PinnedHelper> {
    public PinnedHelper(HelperSecret s) {}

    /* NAVIGATION */

    public ConversationHelper goConversation() {
        pressBack();

        return new ConversationHelper(new HelperSecret());
    }
}
