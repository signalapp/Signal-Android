package org.thoughtcrime.securesms;

import android.content.Intent;
import android.support.test.InstrumentationRegistry;
import android.support.test.espresso.NoMatchingViewException;
import android.support.test.espresso.UiController;
import android.support.test.espresso.ViewAction;
import android.support.test.rule.ActivityTestRule;
import android.view.View;
import android.widget.TextView;

import org.hamcrest.Matcher;

import java.util.UUID;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static android.support.test.espresso.Espresso.pressBack;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.action.ViewActions.longClick;
import static android.support.test.espresso.action.ViewActions.typeText;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition;
import static android.support.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;

enum Location {
    HOME,
    CONVERSATION,
    MESSAGE_SELECTED,
    PINNED,
}

public class EspressoHelper {
    private String phoneNumber;
    private Location location;

    public EspressoHelper(ActivityTestRule<ConversationListActivity> activityRule) {
        activityRule.launchActivity(new Intent());

        openActionBarOverflowOrOptionsMenu(InstrumentationRegistry.getTargetContext());

        onView(withText(R.string.text_secure_normal__menu_settings))
                .perform(click());

        final String[] stringHolder = { null };
        onView(withId(R.id.number)).perform(new ViewAction() {
            @Override
            public Matcher<View> getConstraints() {
                return isAssignableFrom(TextView.class);
            }

            @Override
            public String getDescription() {
                return "getting text from a TextView";
            }

            @Override
            public void perform(UiController uiController, View view) {
                TextView tv = (TextView)view; //Save, because of check in getConstraints()
                stringHolder[0] = tv.getText().toString();
            }
        });

        this.phoneNumber = stringHolder[0];

        pressBack();

        this.location = Location.HOME;
    }

    public void done() {
        if (this.location != Location.HOME) {
            this.goHome();
        }
    }

    /** ASSERTIONS */

    public EspressoHelper findId (int id) {
        onView(withId(id))
                .check(matches(isDisplayed()));

        return this;
    }

    public EspressoHelper findText (String text) {
        onView(withText(text))
                .check(matches(isDisplayed()));

        return this;
    }

    public EspressoHelper not_findText (String text) {
        try {
            onView(withText(text))
                    .check(matches(isDisplayed()));

            throw new Error("EspressoHelper.not_findText: View found matching \"" + text + "\"");
        } catch (NoMatchingViewException e) {}

        return this;
    }

    /** NAVIGATION */

    public EspressoHelper goHome() {
        switch (this.location) {
            case HOME:
                break;
            case CONVERSATION:
                pressBack();
                break;
            case MESSAGE_SELECTED:
                pressBack();
                pressBack();
                break;
            case PINNED:
                pressBack();
                pressBack();
                break;
            default:
                throw new LocationError("UNKNOWN");
        }

        this.location = Location.HOME;

        return this;
    }

    public EspressoHelper goConversation() {
        this.goHome();

        return this.createConversation();
    }

    public EspressoHelper goPinned() {
        if (this.location != Location.CONVERSATION) {
            throw new LocationError("CONVERSATION");
        }

        openActionBarOverflowOrOptionsMenu(InstrumentationRegistry.getTargetContext());

        onView(withText(R.string.conversation__menu_view_pinned_messages))
                .perform(click());

        this.location = Location.PINNED;

        return this;
    }

    /* ACTIONS */

    public EspressoHelper createConversation() {
        if (this.location != Location.HOME) {
            throw new LocationError("HOME");
        }

        onView(withId(R.id.fab))
                .perform(click());
        onView(withId(R.id.search_view))
                .perform(typeText(this.phoneNumber));
        onView(withId(R.id.name))
                .perform(click());

        this.location = Location.CONVERSATION;

        return this;
    }

    public EspressoHelper sendMessage() {
        return this.sendMessage("Hello World!");
    }

    public EspressoHelper sendMessage(String message) {
        if (this.location != Location.CONVERSATION) {
            throw new LocationError("CONVERSATION");
        }

        onView(withId(R.id.embedded_text_editor))
                .perform(typeText(message));
        onView(withId(R.id.send_button))
                .perform(click());

        return this;
    }

    public EspressoHelper selectMessage() {
        return this.selectMessage(0);
    }

    public EspressoHelper selectMessage(int position) {
        if (this.location != Location.CONVERSATION) {
            throw new LocationError("CONVERSATION");
        }

        onView(withId(android.R.id.list))
                .perform(actionOnItemAtPosition(position, longClick()));

        this.location = Location.MESSAGE_SELECTED;

        return this;
    }

    public EspressoHelper pinSelectedMessage() {
        if (this.location != Location.MESSAGE_SELECTED) {
            throw new LocationError("MESSAGE_SELECTED");
        }

        onView(withId(R.id.menu_context_pin_message))
                .perform(click());

        this.location = Location.CONVERSATION;

        return this;
    }

    public EspressoHelper unpinSelectedMessage() {
        if (this.location != Location.MESSAGE_SELECTED) {
            throw new LocationError("MESSAGE_SELECTED");
        }

        onView(withId(R.id.menu_context_unpin_message))
                .perform(click());

        this.location = Location.CONVERSATION;

        return this;
    }

    /* MISC */

    public String randString() {
        return UUID.randomUUID().toString().substring(0, 16);
    }

    private class LocationError extends Error {
        private String expectedLocation;

        public LocationError(String expectedLocation) {
            this.expectedLocation = expectedLocation;
        }

        public String toString() {
            return "LocationError: expected " + this.expectedLocation;
        }
    }
}
