package org.thoughtcrime.securesms.espresso;

import android.content.Intent;
import android.support.test.InstrumentationRegistry;
import android.support.test.espresso.NoMatchingViewException;
import android.support.test.espresso.UiController;
import android.support.test.espresso.ViewAction;
import android.support.test.espresso.matcher.ViewMatchers;
import android.support.test.rule.ActivityTestRule;
import android.view.View;
import android.widget.TextView;

import org.hamcrest.Matcher;
import org.thoughtcrime.securesms.ConversationListActivity;
import org.thoughtcrime.securesms.R;

import java.util.UUID;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static android.support.test.espresso.Espresso.pressBack;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;

class HelperSecret {};

public class Helper<T> {
    static String phoneNumber = null;

    Helper() {}

    public Helper(ActivityTestRule<ConversationListActivity> activityRule) {
        activityRule.launchActivity(new Intent());

        if (Helper.phoneNumber == null) {
            new Chad();
        }
    }

    /* NAVIGATION */

    public ConversationsHelper goConversations() {
        return new ConversationsHelper(new HelperSecret());
    }

    /* ASSERTIONS */

    public T assertID(int id) {
        onView(withId(id))
                .check(matches(isDisplayed()));

        return (T)this;
    }

    public T assertText(String text) {
        onView(withText(text))
                .check(matches(isDisplayed()));

        return (T)this;
    }

    public T assertNoText(String text) {
        try {
            onView(withText(text))
                    .check(matches(isDisplayed()));

            throw new Error("Helper.assertNoText: View found matching \"" + text + "\"");
        } catch (NoMatchingViewException e) {}

        return (T)this;
    }

    /* MISC */

    public String randString() {
        return UUID.randomUUID().toString().substring(0, 16);
    }
}

class Chad {
    public Chad() {
        openActionBarOverflowOrOptionsMenu(InstrumentationRegistry.getTargetContext());

        onView(ViewMatchers.withText(R.string.text_secure_normal__menu_settings))
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
                TextView textView = (TextView)view; //Save, because of check in getConstraints()
                stringHolder[0] = textView.getText().toString();
            }
        });

        pressBack();

        Helper.phoneNumber = stringHolder[0];
    }
}
