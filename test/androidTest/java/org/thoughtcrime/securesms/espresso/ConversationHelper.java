package org.thoughtcrime.securesms.espresso;

import android.support.test.InstrumentationRegistry;

import org.thoughtcrime.securesms.R;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static android.support.test.espresso.Espresso.pressBack;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.action.ViewActions.longClick;
import static android.support.test.espresso.action.ViewActions.typeText;
import static android.support.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;

public class ConversationHelper extends Helper<ConversationHelper> {
    private Boolean messageSelected = false;

    ConversationHelper(HelperSecret s) {}

    public ConversationHelper sendMessage(String message) {
        onView(withId(R.id.embedded_text_editor))
                .perform(typeText(message));
        onView(withId(R.id.send_button))
                .perform(click());

        return this;
    }

    public ConversationHelper selectMessage(int position) {
        this.messageSelected = true;

        onView(withId(android.R.id.list))
                .perform(actionOnItemAtPosition(position, longClick()));

        return this;
    }

    public ConversationHelper unselectMessage() {
        if (!this.messageSelected) {
            return this;
        }

        this.messageSelected = false;
        pressBack();

        return this;
    }

    public ConversationHelper pinMessage(int position) {
        this.unselectMessage();

        this.selectMessage(position);

        onView(withId(R.id.menu_context_pin_message))
                .perform(click());

        return this;
    }

    public ConversationHelper unpinMessage(int position) {
        this.unselectMessage();

        this.selectMessage(position);

        onView(withId(R.id.menu_context_unpin_message))
                .perform(click());

        return this;
    }

    /* NAVIGATION */

    public ConversationsHelper goConversations() {
        if (this.messageSelected) {
            pressBack();
        }

        pressBack();

        return new ConversationsHelper(new HelperSecret());
    }

    public PinnedHelper goPinned() {
        openActionBarOverflowOrOptionsMenu(InstrumentationRegistry.getTargetContext());

        onView(withText(R.string.conversation__menu_view_pinned_messages))
                .perform(click());

        return new PinnedHelper(new HelperSecret());
    }
}
